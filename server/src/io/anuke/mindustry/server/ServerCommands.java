package io.anuke.mindustry.server;

import io.anuke.arc.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.util.*;
import io.anuke.arc.util.CommandHandler.*;
import io.anuke.mindustry.core.GameState.*;
import io.anuke.mindustry.entities.type.*;
import io.anuke.mindustry.game.*;
import io.anuke.mindustry.game.EventType.*;
import io.anuke.mindustry.gen.*;
import io.anuke.mindustry.io.*;
import io.anuke.mindustry.maps.Map;
import io.anuke.mindustry.maps.*;
import io.anuke.mindustry.net.Administration.*;
import io.anuke.mindustry.net.Packets.*;
import io.anuke.mindustry.plugin.Plugins.*;
import io.anuke.mindustry.type.*;

import java.io.*;
import java.net.*;

import static io.anuke.arc.util.Log.*;
import static io.anuke.mindustry.Vars.*;

class ServerCommands {

    static void registerCommands(CommandHandler handler, ServerControl server){
        handler.register("help", "Displays this command list.", arg -> {
            info("Commands:");
            for(Command command : handler.getCommandList()){
                info("   &y" + command.text + (command.paramText.isEmpty() ? "" : " ") + command.paramText + " - &lm" + command.description);
            }
        });

        handler.register("version", "Displays server version info.", arg -> {
            info("&lmVersion: &lyMindustry {0}-{1} {2} / build {3}", Version.number, Version.modifier, Version.type, Version.build);
            info("&lmJava Version: &ly{0}", System.getProperty("java.version"));
        });

        handler.register("exit", "Exit the server application.", arg -> {
            info("Shutting down server.");
            net.dispose();
            Core.app.exit();
        });

        handler.register("stop", "Stop hosting the server.", arg -> {
            net.closeServer();
            if(server.getLastTask() != null) server.getLastTask().cancel();
            state.set(State.menu);
            info("Stopped server.");
        });

        handler.register("host", "<mapname> [mode] [password]", "Open the server with a specific map.", arg -> {
            if(state.is(State.playing)){
                err("Already hosting. Type 'stop' to stop hosting first.");
                return;
            }

            if(server.getLastTask() != null) server.getLastTask().cancel();

            Map result = maps.all().find(map -> map.name().equalsIgnoreCase(arg[0].replace('_', ' ')) || map.name().equalsIgnoreCase(arg[0]));

            if(result == null){
                err("No map with name &y'{0}'&lr found.", arg[0]);
                return;
            }

            Gamemode preset = Gamemode.survival;

            if(arg.length > 1){
                try{
                    preset = Gamemode.valueOf(arg[1]);
                }catch(IllegalArgumentException e){
                    err("No gamemode '{0}' found.", arg[1]);
                    return;
                }
                try{
                    //if there is not a password set set the password to blank
                    if(arg.length == 2){
                        Core.settings.put("pass","");
                    }
                    else {
                        //set password to the entered argument
                        String password = arg[2];
                        Core.settings.put("pass", password);
                    }
                    Core.settings.save();
                }
                catch (IllegalArgumentException e){
                    err("No password Entered");
                    return;
                }
            }

            info("Loading map...");

            logic.reset();
            server.setLastMode(preset);
            try{
                world.loadMap(result,  result.applyRules(server.getLastMode()));
                state.rules = result.applyRules(preset);
                logic.play();

                info("Map loaded.");

                host();
            }catch(MapException e){
                Log.err(e.map.name() + ": " + e.getMessage());
            }
        });

        handler.register("port", "[port]", "Sets or displays the port for hosting the server.", arg -> {
            if(arg.length == 0){
                info("&lyPort: &lc{0}", Core.settings.getInt("port"));
            }else{
                int port = Strings.parseInt(arg[0]);
                if(port < 0 || port > 65535){
                    err("Port must be a number between 0 and 65535.");
                    return;
                }
                info("&lyPort set to {0}.", port);
                Core.settings.put("port", port);
                Core.settings.save();
            }
        });

        handler.register("maps", "Display all available maps.", arg -> {
            if(!maps.all().isEmpty()){
                info("Maps:");
                for(Map map : maps.all()){
                    info("  &ly{0}: &lb&fi{1} / {2}x{3}", map.name(), map.custom ? "Custom" : "Default", map.width, map.height);
                }
            }else{
                info("No maps found.");
            }
            info("&lyMap directory: &lb&fi{0}", customMapDirectory.file().getAbsoluteFile().toString());
        });

        handler.register("reloadmaps", "Reload all maps from disk.", arg -> {
            int beforeMaps = maps.all().size;
            maps.reload();
            if(maps.all().size > beforeMaps){
                info("&lc{0}&ly new map(s) found and reloaded.", maps.all().size - beforeMaps);
            }else{
                info("&lyMaps reloaded.");
            }
        });

        handler.register("status", "Display server status.", arg -> {
            if(state.is(State.menu)){
                info("Status: &rserver closed");
            }else{
                info("Status:");
                info("  &lyPlaying on map &fi{0}&fb &lb/&ly Wave {1}", Strings.capitalize(world.getMap().name()), state.wave);

                if(state.rules.waves){
                    info("&ly  {0} enemies.", unitGroups[Team.crux.ordinal()].size());
                }else{
                    info("&ly  {0} seconds until next wave.", (int)(state.wavetime / 60));
                }

                info("  &ly{0} FPS, {1} MB used.", (int)(60f / Time.delta()), Core.app.getJavaHeap() / 1024 / 1024);

                if(playerGroup.size() > 0){
                    info("  &lyPlayers: {0}", playerGroup.size());
                    for(Player p : playerGroup.all()){
                        info("    &y{0} / {1}", p.name, p.uuid);
                    }
                }else{
                    info("  &lyNo players connected.");
                }
            }
        });

        handler.register("plugins", "Display all loaded plugins.", arg -> {
            if(!plugins.all().isEmpty()){
                info("Plugins:");
                for(LoadedPlugin plugin : plugins.all()){
                    info("  &ly{0} &lcv{1}", plugin.meta.name, plugin.meta.version);
                }
            }else{
                info("No plugins found.");
            }
            info("&lyPlugin directory: &lb&fi{0}", pluginDirectory.file().getAbsoluteFile().toString());
        });

        handler.register("plugin", "<name...>", "Display information about a loaded plugin.", arg -> {
            LoadedPlugin plugin = plugins.all().find(p -> p.meta.name.equalsIgnoreCase(arg[0]));
            if(plugin != null){
                info("Name: &ly{0}", plugin.meta.name);
                info("Version: &ly{0}", plugin.meta.version);
                info("Author: &ly{0}", plugin.meta.author);
                info("Path: &ly{0}", plugin.jarFile.path());
                info("Description: &ly{0}", plugin.meta.description);
            }else{
                info("No plugin with name &ly'{0}'&lg found.");
            }
        });

        handler.register("say", "<message...>", "Send a message to all players.", arg -> {
            if(!state.is(State.playing)){
                err("Not hosting. Host a game first.");
                return;
            }

            Call.sendMessage("[scarlet][[Server]:[] " + arg[0]);

            info("&lyServer: &lb{0}", arg[0]);
        });

        handler.register("difficulty", "<difficulty>", "Set game difficulty.", arg -> {
            try{
                state.rules.waveSpacing = Difficulty.valueOf(arg[0]).waveTime * 60 * 60 * 2;
                info("Difficulty set to '{0}'.", arg[0]);
            }catch(IllegalArgumentException e){
                err("No difficulty with name '{0}' found.", arg[0]);
            }
        });

        handler.register("fillitems", "[team]", "Fill the core with items.", arg -> {
            if(!state.is(State.playing)){
                err("Not playing. Host first.");
                return;
            }

            try{
                Team team = arg.length == 0 ? Team.sharded : Team.valueOf(arg[0]);

                if(state.teams.get(team).cores.isEmpty()){
                    err("That team has no cores.");
                    return;
                }

                for(Item item : content.items()){
                    if(item.type == ItemType.material){
                        state.teams.get(team).cores.first().entity.items.set(item, state.teams.get(team).cores.first().block().itemCapacity);
                    }
                }

                info("Core filled.");
            }catch(IllegalArgumentException ignored){
                err("No such team exists.");
            }
        });

        handler.register("name", "[name...]", "Change the server display name.", arg -> {
            if(arg.length == 0){
                info("Server name is currently &lc'{0}'.", Core.settings.getString("servername"));
                return;
            }
            Core.settings.put("servername", arg[0]);
            Core.settings.save();
            info("Server name is now &lc'{0}'.", arg[0]);
        });

        handler.register("playerlimit", "[off/somenumber]", "Set the server player limit.", arg -> {
            if(arg.length == 0){
                info("Player limit is currently &lc{0}.", netServer.admins.getPlayerLimit() == 0 ? "off" : netServer.admins.getPlayerLimit());
                return;
            }
            if(arg[0].equals("off")){
                netServer.admins.setPlayerLimit(0);
                info("Player limit disabled.");
                return;
            }

            if(Strings.canParsePostiveInt(arg[0]) && Strings.parseInt(arg[0]) > 0){
                int lim = Strings.parseInt(arg[0]);
                netServer.admins.setPlayerLimit(lim);
                info("Player limit is now &lc{0}.", lim);
            }else{
                err("Limit must be a number above 0.");
            }
        });

        handler.register("whitelist", "[on/off...]", "Enable/disable whitelisting.", arg -> {
            if(arg.length == 0){
                info("Whitelist is currently &lc{0}.", netServer.admins.isWhitelistEnabled() ? "on" : "off");
                return;
            }
            boolean on = arg[0].equalsIgnoreCase("on");
            netServer.admins.setWhitelist(on);
            info("Whitelist is now &lc{0}.", on ? "on" : "off");
        });

        handler.register("whitelisted", "List the entire whitelist.", arg -> {
            if(netServer.admins.getWhitelisted().isEmpty()){
                info("&lyNo whitelisted players found.");
                return;
            }

            info("&lyWhitelist:");
            netServer.admins.getWhitelisted().each(p -> Log.info("- &ly{0}", p.lastName));
        });

        handler.register("whitelist-add", "<ID>", "Add a player to the whitelist by ID.", arg -> {
            PlayerInfo info = netServer.admins.getInfoOptional(arg[0]);
            if(info == null){
                err("Player ID not found. You must use the ID displayed when a player joins a server.");
                return;
            }

            netServer.admins.whitelist(arg[0]);
            info("Player &ly'{0}'&lg has been whitelisted.", info.lastName);
        });

        handler.register("whitelist-remove", "<ID>", "Remove a player to the whitelist by ID.", arg -> {
            PlayerInfo info = netServer.admins.getInfoOptional(arg[0]);
            if(info == null){
                err("Player ID not found. You must use the ID displayed when a player joins a server.");
                return;
            }

            netServer.admins.unwhitelist(arg[0]);
            info("Player &ly'{0}'&lg has been un-whitelisted.", info.lastName);
        });

        handler.register("crashreport", "<on/off>", "Disables or enables automatic crash reporting", arg -> {
            boolean value = arg[0].equalsIgnoreCase("on");
            Core.settings.put("crashreport", value);
            Core.settings.save();
            info("Crash reporting is now {0}.", value ? "on" : "off");
        });

        handler.register("logging", "<on/off>", "Disables or enables server logs", arg -> {
            boolean value = arg[0].equalsIgnoreCase("on");
            Core.settings.put("logging", value);
            Core.settings.save();
            info("Logging is now {0}.", value ? "on" : "off");
        });

        handler.register("strict", "<on/off>", "Disables or enables strict mode", arg -> {
            boolean value = arg[0].equalsIgnoreCase("on");
            netServer.admins.setStrict(value);
            info("Strict mode is now {0}.", netServer.admins.getStrict() ? "on" : "off");
        });

        handler.register("socketinput", "[on/off]", "Disables or enables a local TCP socket at port "+ server.getCommandSocketPort() +" to recieve commands from other applications", arg -> {
            if(arg.length == 0){
                info("Socket input is currently &lc{0}.", Core.settings.getBool("socket") ? "on" : "off");
                return;
            }

            boolean value = arg[0].equalsIgnoreCase("on");
            server.toggleSocket(value);
            Core.settings.put("socket", value);
            Core.settings.save();
            info("Socket input is now &lc{0}.", value ? "on" : "off");
        });

        handler.register("allow-custom-clients", "[on/off]", "Allow or disallow custom clients.", arg -> {
            if(arg.length == 0){
                info("Custom clients are currently &lc{0}.", netServer.admins.allowsCustomClients() ? "allowed" : "disallowed");
                return;
            }

            String s = arg[0];
            if(s.equalsIgnoreCase("on")){
                netServer.admins.setCustomClients(true);
                info("Custom clients enabled.");
            }else if(s.equalsIgnoreCase("off")){
                netServer.admins.setCustomClients(false);
                info("Custom clients disabled.");
            }else{
                err("Incorrect command usage.");
            }
        });

        handler.register("shuffle", "<on/off>", "Set map shuffling.", arg -> {
            if(!arg[0].equals("on") && !arg[0].equals("off")){
                err("Invalid shuffle mode.");
                return;
            }
            Core.settings.put("shuffle", arg[0].equals("on"));
            Core.settings.save();
            info("Shuffle mode set to '{0}'.", arg[0]);
        });

        handler.register("kick", "<username...>", "Kick a person by name.", arg -> {
            if(!state.is(State.playing)){
                err("Not hosting a game yet. Calm down.");
                return;
            }

            Player target = playerGroup.find(p -> p.name.equals(arg[0]));

            if(target != null){
                Call.sendMessage("[scarlet] " + target.name + "[scarlet] has been kicked by the server.");
                target.con.kick(KickReason.kick);
                info("It is done.");
            }else{
                info("Nobody with that name could be found...");
            }
        });

        handler.register("ban", "<type-id/name/ip> <username/IP/ID...>", "Ban a person.", arg -> {
            if(arg[0].equals("id")){
                netServer.admins.banPlayerID(arg[1]);
                info("Banned.");
            }else if(arg[0].equals("name")){
                Player target = playerGroup.find(p -> p.name.equalsIgnoreCase(arg[1]));
                if(target != null){
                    netServer.admins.banPlayer(target.uuid);
                    info("Banned.");
                }else{
                    err("No matches found.");
                }
            }else if(arg[0].equals("ip")){
                netServer.admins.banPlayerIP(arg[1]);
                info("Banned.");
            }else{
                err("Invalid type.");
            }

            for(Player player : playerGroup.all()){
                if(netServer.admins.isIDBanned(player.uuid)){
                    Call.sendMessage("[scarlet] " + player.name + " has been banned.");
                    player.con.kick(KickReason.banned);
                }
            }
        });

        handler.register("bans", "List all banned IPs and IDs.", arg -> {
            Array<PlayerInfo> bans = netServer.admins.getBanned();

            if(bans.size == 0){
                info("No ID-banned players have been found.");
            }else{
                info("&lyBanned players [ID]:");
                for(PlayerInfo info : bans){
                    info(" &ly {0} / Last known name: '{1}'", info.id, info.lastName);
                }
            }

            Array<String> ipbans = netServer.admins.getBannedIPs();

            if(ipbans.size == 0){
                info("No IP-banned players have been found.");
            }else{
                info("&lmBanned players [IP]:");
                for(String string : ipbans){
                    PlayerInfo info = netServer.admins.findByIP(string);
                    if(info != null){
                        info(" &lm '{0}' / Last known name: '{1}' / ID: '{2}'", string, info.lastName, info.id);
                    }else{
                        info(" &lm '{0}' (No known name or info)", string);
                    }
                }
            }
        });

        handler.register("unban", "<ip/ID>", "Completely unban a person by IP or ID.", arg -> {
            if(arg[0].contains(".")){
                if(netServer.admins.unbanPlayerIP(arg[0])){
                    info("Unbanned player by IP: {0}.", arg[0]);
                }else{
                    err("That IP is not banned!");
                }
            }else{
                if(netServer.admins.unbanPlayerID(arg[0])){
                    info("Unbanned player by ID: {0}.", arg[0]);
                }else{
                    err("That ID is not banned!");
                }
            }
        });

        handler.register("admin", "<username...>", "Make an online user admin", arg -> {
            if(!state.is(State.playing)){
                err("Open the server first.");
                return;
            }

            Player target = playerGroup.find(p -> p.name.equals(arg[0]));

            if(target != null){
                netServer.admins.adminPlayer(target.uuid, target.usid);
                target.isAdmin = true;
                info("Admin-ed player: {0}", arg[0]);
            }else{
                info("Nobody with that name could be found.");
            }
        });

        handler.register("unadmin", "<username...>", "Removes admin status from an online player", arg -> {
            if(!state.is(State.playing)){
                err("Open the server first.");
                return;
            }

            Player target = playerGroup.find(p -> p.name.equals(arg[0]));

            if(target != null){
                netServer.admins.unAdminPlayer(target.uuid);
                target.isAdmin = false;
                info("Un-admin-ed player: {0}", arg[0]);
            }else{
                info("Nobody with that name could be found.");
            }
        });

        handler.register("admins", "List all admins.", arg -> {
            Array<PlayerInfo> admins = netServer.admins.getAdmins();

            if(admins.size == 0){
                info("No admins have been found.");
            }else{
                info("&lyAdmins:");
                for(PlayerInfo info : admins){
                    info(" &lm {0} /  ID: '{1}' / IP: '{2}'", info.lastName, info.id, info.lastIP);
                }
            }
        });

        handler.register("runwave", "Trigger the next wave.", arg -> {
            if(!state.is(State.playing)){
                err("Not hosting. Host a game first.");
            }else{
                logic.runWave();
                info("Wave spawned.");
            }
        });

        handler.register("load", "<slot>", "Load a save from a slot.", arg -> {
            if(state.is(State.playing)){
                err("Already hosting. Type 'stop' to stop hosting first.");
                return;
            }else if(!Strings.canParseInt(arg[0])){
                err("Invalid save slot '{0}'.", arg[0]);
                return;
            }

            int slot = Strings.parseInt(arg[0]);

            if(!SaveIO.isSaveValid(slot)){
                err("No (valid) save data found for slot.");
                return;
            }

            Core.app.post(() -> {
                try{
                    SaveIO.loadFromSlot(slot);
                }catch(Throwable t){
                    err("Failed to load save. Outdated or corrupt file.");
                }
                info("Save loaded.");
                host();
                state.set(State.playing);
            });
        });

        handler.register("save", "<slot>", "Save game state to a slot.", arg -> {
            if(!state.is(State.playing)){
                err("Not hosting. Host a game first.");
                return;
            }else if(!Strings.canParseInt(arg[0])){
                err("Invalid save slot '{0}'.", arg[0]);
                return;
            }

            Core.app.post(() -> {
                int slot = Strings.parseInt(arg[0]);
                SaveIO.saveToSlot(slot);
                info("Saved to slot {0}.", slot);
            });
        });

        handler.register("gameover", "Force a game over.", arg -> {
            if(state.is(State.menu)){
                info("Not playing a map.");
                return;
            }

            info("&lyCore destroyed.");
            server.setInExtraRound(false);
            Events.fire(new GameOverEvent(Team.crux));
        });

        handler.register("info", "<IP/UUID/name...>", "Find player info(s). Can optionally check for all names or IPs a player has had.", arg -> {

            ObjectSet<PlayerInfo> infos = netServer.admins.findByName(arg[0]);

            if(infos.size > 0){
                info("&lgPlayers found: {0}", infos.size);

                int i = 0;
                for(PlayerInfo info : infos){
                    info("&lc[{0}] Trace info for player '{1}' / UUID {2}", i++, info.lastName, info.id);
                    info("  &lyall names used: {0}", info.names);
                    info("  &lyIP: {0}", info.lastIP);
                    info("  &lyall IPs used: {0}", info.ips);
                    info("  &lytimes joined: {0}", info.timesJoined);
                    info("  &lytimes kicked: {0}", info.timesKicked);
                }
            }else{
                info("Nobody with that name could be found.");
            }
        });

        handler.register("gc", "Trigger a grabage collection. Testing only.", arg -> {
            int pre = (int)(Core.app.getJavaHeap() / 1024 / 1024);
            System.gc();
            int post = (int)(Core.app.getJavaHeap() / 1024 / 1024);
            info("&ly{0}&lg MB collected. Memory usage now at &ly{1}&lg MB.", pre - post, post);
        });

        plugins.each(p -> p.registerServerCommands(handler));
        plugins.each(p -> p.registerClientCommands(netServer.clientCommands));
    }

    private static void host(){
        try{
            net.host(Core.settings.getInt("port"));
            info("&lcOpened a server on port {0}.", Core.settings.getInt("port"));
        }catch(BindException e){
            Log.err("Unable to host: Port already in use! Make sure no other servers are running on the same port in your network.");
            state.set(State.menu);
        }catch(IOException e){
            err(e);
            state.set(State.menu);
        }
    }

}
