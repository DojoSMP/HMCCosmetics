<p align="center">
    <a href="https://www.spigotmc.org/resources/100107/">
        <img alt="logo" src="https://www.spigotmc.org/data/resource_icons/100/100107.jpg?1645162659"/>
    </a>
</p>

<h4 align="center">Source code for HMCCosmetics, a premium Minecraft plugin.</h4>

<p align="center">
    <a href="https://www.spigotmc.org/resources/100107/">
        <img alt="spigot" src="https://img.shields.io/badge/SPIGOT-HMCCosmetics-yellow?style=for-the-badge"/>
    </a>
    <a href="https://polymart.org/resource/1879">
        <img alt="polymart" src="https://img.shields.io/badge/POLYMART-HMCCosmetics-brightgreen?style=for-the-badge"/>
    </a>
    <a href="https://docs.hibiscusmc.com/">
        <img alt="docs" src="https://img.shields.io/badge/Documentation-brightgreen?style=for-the-badge"/>
    </a>
    <a href="https://discord.gg/pcm8kWrdNt">
        <img alt="discord" src="https://img.shields.io/badge/Discord Support-blue?style=for-the-badge"/>
    </a>
</p> 

### DojoLibrary vanish integration

The DojoCosmetics optional `@Hook("HMCCosmetics")` adapter opens the configured `cosmenu` from its
Wardrobe category and reads DojoLibrary's cached vanish API on player entity threads. This fork exposes
`CosmeticUser.HiddenReason.DOJO_VANISH` so the adapter can hide/show only the Dojo-owned reason without
clearing command, WorldGuard, potion, or other vanish flags. Deploy the accompanying DojoCosmetics build.
HMC's native vanish detection and scheduler remain unchanged. The adapter owns registration, one-second
cache reconciliation, listeners, and shutdown; no new HMC tasks or persistence are introduced.
Unvanish restores only the Dojo reason. A detached adapter retains the privacy flag until reactivation or
user-session recreation. Live integration with the production DojoLibrary stack remains unverified.

### Wardrobe return navigation

The HMC fork ships `menus/cosmenu.yml`, created on startup only if that file is absent. It links the four default
model categories and has a **Back** arrow that runs `[PLAYER-COMMAND] dojocosmetics:cosmetic` as the viewing player.
Default HMC category templates also include this Back arrow. No console privileges are used; player-command actions
run on the player's entity scheduler one tick after the click so inventory transitions occur outside the click event.
Existing operator menu files are preserved. For an existing custom `cosmenu`, add an `empty` item with an ARROW icon
and `actions.any: ["[PLAYER-COMMAND] dojocosmetics:cosmetic"]` in a free slot, then reload HMC menus.
The default `cosmenu` Back arrow uses zero-based slot 22; default category templates use slot 45.
DojoCosmetics owns the returned Main Menu; HMC owns the outgoing menu and cancels its refresh on close.
Live return navigation on a server with DojoLibrary has not been verified.
