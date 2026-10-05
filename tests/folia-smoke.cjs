// Run against an isolated, configured Folia server (offline mode, default cosmetics/wardrobe).
// FOLIA_SERVER_DIR=/path/to/server MINEFLAYER_MODULE=/path/to/mineflayer JAVA=/path/to/java node tests/folia-smoke.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const mineflayer = require(process.env.MINEFLAYER_MODULE || 'mineflayer');
const dir = process.env.FOLIA_SERVER_DIR;
assert(dir, 'FOLIA_SERVER_DIR must point to an isolated Folia server');
const properties = fs.readFileSync(path.join(dir, 'server.properties'), 'utf8');
assert(/^online-mode=false$/m.test(properties), 'Test server must use offline mode');
assert(/return-last-location: true/.test(fs.readFileSync(path.join(dir, 'plugins/HMCCosmetics/config.yml'), 'utf8')), 'Enable wardrobe.return-last-location for cross-region return checks');
const port = Number(properties.match(/^server-port=(\d+)$/m)[1]);
const server = spawn(process.env.JAVA || 'java', ['-Xms512M', '-Xmx2G', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'folia.jar', 'nogui'], { cwd: dir });
let log = '';
server.stdout.on('data', chunk => { log += chunk; });
server.stderr.on('data', chunk => { log += chunk; });
const command = text => server.stdin.write(text + '\n');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(predicate, label, timeout = 15000) {
  const deadline = Date.now() + timeout;
  while (!predicate()) {
    assert(Date.now() < deadline, 'Timed out: ' + label + '\n' + log.slice(-3000));
    await sleep(100);
  }
}
async function connect(username) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port, username, auth: 'offline', version: '1.21.11' });
  const messages = [];
  bot.on('messagestr', message => messages.push(message));
  bot.on('error', error => messages.push(String(error)));
  await new Promise((resolve, reject) => { bot.once('spawn', resolve); bot.once('error', reject); bot.once('kicked', reject); });
  bot.physicsEnabled = false;
  bot.messages = messages;
  return bot;
}
(async () => {
  let one, two;
  try {
    await until(() => /Done \(/.test(log), 'server startup', 120000);
    assert(/HMCCosmetics/.test(log));
    one = await connect('FoliaOne');
    two = await connect('FoliaTwo');
    for (const name of ['FoliaOne', 'FoliaTwo']) { command('op ' + name); command('gamemode creative ' + name); }
    command('tp FoliaOne 0 70 0');
    command('tp FoliaTwo 2000 70 2000');
    await until(() => two.entity.position.x > 1900, 'separate regions');
    await sleep(1500); // Wait for database load and cosmetic initialization.
    for (const cosmetic of ['beanie', 'backpack', 'kite']) {
      const before = two.messages.length;
      one.chat('/cosmetics apply ' + cosmetic + ' FoliaTwo');
      await until(() => two.messages.length > before && two.messages.slice(before).some(m => /equip/i.test(m)), 'equip ' + cosmetic);
    }
    one.chat('/cosmetics menu defaultmenu_hats FoliaTwo');
    await until(() => two.currentWindow != null, 'cross-region menu');
    await sleep(500); // Exercise the repeating menu task before cancelling it.
    two.closeWindow(two.currentWindow);
    one.chat('/cosmetics wardrobes default FoliaTwo');
    await until(() => Math.abs(two.entity.position.x) < 10, 'wardrobe entry');
    await sleep(1000);
    one.chat('/cosmetics wardrobes default FoliaTwo');
    await until(() => two.entity.position.x > 1900, 'wardrobe exit');
    for (const cmd of ['hide FoliaTwo', 'show FoliaTwo', 'disableall true', 'disableall false', 'reload']) {
      one.chat('/cosmetics ' + cmd);
      await sleep(500);
    }
    two.quit();
    await sleep(1000);
    two = await connect('FoliaTwo');
    await sleep(1500);
    one.chat('/cosmetics apply kite FoliaOne');
    await sleep(500);
    one.chat('/cosmetics wardrobes default FoliaOne');
    await sleep(1500);
    command('stop');
    await until(() => server.exitCode !== null, 'shutdown', 60000);
    assert.equal(server.exitCode, 0);
    const { DatabaseSync } = require('node:sqlite');
    const db = new DatabaseSync(path.join(dir, 'plugins/HMCCosmetics/database.db'), { readOnly: true });
    const saved = db.prepare('SELECT COSMETICS FROM COSMETICDATABASE WHERE UUID = ?').get('018dd1dc-88fe-3be0-a6d3-85661c6dbde4');
    assert(saved && /beanie/.test(saved.COSMETICS) && /kite/.test(saved.COSMETICS), 'Cosmetics must survive reconnect and shutdown');
    db.close();
    assert(!/ERROR|Exception|thread check|Cannot invoke|not owned/i.test(log), log);
    console.log('PASS: cross-region cosmetics, menu, wardrobe, reload, reconnect, and shutdown');
  } finally {
    one?.quit(); two?.quit();
    if (server.exitCode === null) {
      command('stop');
      await until(() => server.exitCode !== null, 'cleanup shutdown', 60000).catch(() => server.kill('SIGTERM'));
    }
    fs.writeFileSync(path.join(dir, 'smoke-result.log'), log);
  }
})().then(() => process.exit(0), error => { console.error(error); process.exit(1); });
