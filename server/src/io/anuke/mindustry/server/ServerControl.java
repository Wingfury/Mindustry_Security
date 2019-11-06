package io.anuke.mindustry.server;

import io.anuke.arc.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.collection.Array.*;
import io.anuke.arc.files.*;
import io.anuke.arc.util.*;
import io.anuke.arc.util.Timer;
import io.anuke.arc.util.CommandHandler.*;
import io.anuke.arc.util.Timer.*;
import io.anuke.mindustry.*;
import io.anuke.mindustry.core.GameState;
import io.anuke.mindustry.core.GameState.*;
import io.anuke.mindustry.entities.*;
import io.anuke.mindustry.entities.type.*;
import io.anuke.mindustry.game.*;
import io.anuke.mindustry.game.EventType.*;
import io.anuke.mindustry.gen.*;
import io.anuke.mindustry.maps.Map;
import io.anuke.mindustry.maps.*;
import io.anuke.mindustry.net.Packets.*;
import io.anuke.mindustry.plugin.*;

import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

import static io.anuke.arc.util.Log.*;
import static io.anuke.mindustry.Vars.*;

public class ServerControl implements ApplicationListener{
    private static final int ROUND_EXTRA_TIME = 12;
    //in bytes: 512 kb is max
    private static final int MAX_LOG_LENGTH = 1024 * 512;
    private static final int COMMAND_SOCKET_PORT = 6859;

    private final CommandHandler handler = new CommandHandler("");
    private final FileHandle logFolder = Core.settings.getDataDirectory().child("logs/");

    private FileHandle currentLogFile;
    private boolean inExtraRound;
    private Task lastTask;
    private Gamemode lastMode = Gamemode.survival;

    private Thread socketThread;
    private PrintWriter socketOutput;

    public ServerControl(String[] args){
        plugins = new Plugins();

        Core.settings.defaults(
            "shufflemode", "normal",
            "bans", "",
            "admins", "",
            "shuffle", true,
            "crashreport", false,
            "port", port,
            "logging", true,
            "socket", false
        );

        /*
        This is the beginning of a lot of best practices errors. Best practices indicate there shouldn't be any
        println and that there should not be any log without an if determining if the statement should be logged.
        The static analysis of the code determined that the info method went to the logger. However, because of this
        override it does not. This architecture allows the server to output information to the console
        with the correct tags. There is no other way to output info to the console in a real time way except with
        println. This means that this design is a good decision and even though in the strict sense violates best
        practices the design makes sense in this application.
         */
        Log.setLogger(new LogHandler(){
            DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("MM-dd-yyyy | HH:mm:ss");

            @Override
            public void debug(String text, Object... args){
                print("&lc&fb" + "[DEBG] " + text, args);
            }

            @Override
            public void info(String text, Object... args){
                print("&lg&fb" + "[INFO] " + text, args);
            }

            @Override
            public void err(String text, Object... args){
                print("&lr&fb" + "[ERR!] " + text, args);
            }

            @Override
            public void warn(String text, Object... args){
                print("&ly&fb" + "[WARN] " + text, args);
            }

            @Override
            public void print(String text, Object... args){
                String result = "[" + dateTime.format(LocalDateTime.now()) + "] " + format(text + "&fr", args);
                System.out.println(result);

                if(Core.settings.getBool("logging")){
                    logToFile("[" + dateTime.format(LocalDateTime.now()) + "] " + format(text + "&fr", false, args));
                }

                if(socketOutput != null){
                    try{
                        socketOutput.println(format(text + "&fr", false, args).replace("[DEBG] ", "").replace("[WARN] ", "").replace("[INFO] ", "").replace("[ERR!] ", ""));
                    }catch(Throwable e){
                        err("Error occurred logging to socket: {0}", e.getClass().getSimpleName());
                    }
                }
            }
        });

        Time.setDeltaProvider(() -> Core.graphics.getDeltaTime() * 60f);
        Effects.setScreenShakeProvider((a, b) -> {});
        Effects.setEffectProvider((a, b, c, d, e, f) -> {});

        //load plugins
        plugins.load();

        ServerCommands.registerCommands(handler, this);

        Core.app.post(() -> {
            String[] commands = {};

            if(args.length > 0){
                commands = Strings.join(" ", args).split(",");
                info("&lmFound {0} command-line arguments to parse.", commands.length);
            }

            for(String s : commands){
                CommandResponse response = handler.handleMessage(s);
                if(response.type != ResponseType.valid){
                    err("Invalid command argument sent: '{0}': {1}", s, response.type.name());
                    err("Argument usage: &lc<command-1> <command1-args...>,<command-2> <command-2-args2...>");
                    System.exit(1);
                }
            }
        });

        customMapDirectory.mkdirs();
        pluginDirectory.mkdirs();

        Thread thread = new Thread(this::readCommands, "Server Controls");
        thread.setDaemon(true);
        thread.start();

        if(Version.build == -1){
            warn("&lyYour server is running a custom build, which means that client checking is disabled.");
            warn("&lyIt is highly advised to specify which version you're using by building with gradle args &lc-Pbuildversion=&lm<build>&ly.");
        }

        Events.on(GameOverEvent.class, event -> {
            if(inExtraRound) return;
            info("Game over!");

            if(Core.settings.getBool("shuffle")){
                if(maps.all().size > 0){
                    Array<Map> maps = Vars.maps.customMaps().size == 0 ? Vars.maps.defaultMaps() : Vars.maps.customMaps();

                    Map previous = world.getMap();
                    Map map = maps.random(previous);

                    Call.onInfoMessage((state.rules.pvp
                    ? "[YELLOW]The " + event.winner.name() + " team is victorious![]" : "[SCARLET]Game over![]")
                    + "\nNext selected map:[accent] " + map.name() + "[]"
                    + (map.tags.containsKey("author") && !map.tags.get("author").trim().isEmpty() ? " by[accent] " + map.author() + "[]" : "") + "." +
                    "\nNew game begins in " + ROUND_EXTRA_TIME + "[] seconds.");

                    info("Selected next map to be {0}.", map.name());

                    play(true, () -> world.loadMap(map,  map.applyRules(lastMode)));
                }
            }else{
                netServer.kickAll(KickReason.gameover);
                state.set(State.menu);
                net.closeServer();
            }
        });

        //initialize plugins
        plugins.each(io.anuke.mindustry.plugin.Plugin::init);

        if(!plugins.all().isEmpty()){
            info("&lc{0} plugins loaded.", plugins.all().size);
        }

        info("&lcServer loaded. Type &ly'help'&lc for help.");
        System.out.print("> ");

        if(Core.settings.getBool("socket")){
            toggleSocket(true);
        }
    }



    private void readCommands(){

        Scanner scan = new Scanner(System.in);
        while(scan.hasNext()){
            String line = scan.nextLine();
            Core.app.post(() -> handleCommandString(line));
        }
    }

    private void handleCommandString(String line){
        CommandResponse response = handler.handleMessage(line);

        if(response.type == ResponseType.unknownCommand){

            int minDst = 0;
            Command closest = null;

            for(Command command : handler.getCommandList()){
                int dst = Strings.levenshtein(command.text, response.runCommand);
                if(dst < 3 && (closest == null || dst < minDst)){
                    minDst = dst;
                    closest = command;
                }
            }

            if(closest != null){
                err("Command not found. Did you mean \"" + closest.text + "\"?");
            }else{
                err("Invalid command. Type 'help' for help.");
            }
        }else if(response.type == ResponseType.fewArguments){
            err("Too few command arguments. Usage: " + response.command.text + " " + response.command.paramText);
        }else if(response.type == ResponseType.manyArguments){
            err("Too many command arguments. Usage: " + response.command.text + " " + response.command.paramText);
        }

        System.out.print("> ");
    }

    private void play(boolean wait, Runnable run){
        inExtraRound = true;
        Runnable r = () -> {

            Array<Player> players = new Array<>();
            for(Player p : playerGroup.all()){
                players.add(p);
                p.setDead(true);
            }
            
            logic.reset();

            Call.onWorldDataBegin();
            run.run();
            logic.play();
            state.rules = world.getMap().applyRules(lastMode);

            for(Player p : players){
                p.reset();
                if(state.rules.pvp){
                    p.setTeam(netServer.assignTeam(p, new ArrayIterable<>(players)));
                }
                netServer.sendWorldData(p);
            }
            inExtraRound = false;
        };

        if(wait){
            lastTask = new Task(){
                @Override
                public void run(){
                    try{
                        r.run();
                    }catch(MapException e){
                        Log.err(e.map.name() + ": " + e.getMessage());
                        net.closeServer();
                    }
                }
            };

            Timer.schedule(lastTask, ROUND_EXTRA_TIME);
        }else{
            r.run();
        }
    }



    private void logToFile(String text){
        if(currentLogFile != null && currentLogFile.length() > MAX_LOG_LENGTH){
            String date = DateTimeFormatter.ofPattern("MM-dd-yyyy | HH:mm:ss").format(LocalDateTime.now());
            currentLogFile.writeString("[End of log file. Date: " + date + "]\n", true);
            currentLogFile = null;
        }

        if(currentLogFile == null){
            int i = 0;
            while(logFolder.child("log-" + i + ".txt").length() >= MAX_LOG_LENGTH){
                i++;
            }

            currentLogFile = logFolder.child("log-" + i + ".txt");
        }

        currentLogFile.writeString(text + "\n", true);
    }

    void toggleSocket(boolean on){
        if(on && socketThread == null){
            socketThread = new Thread(() -> {
                try{
                    try(ServerSocket socket = new ServerSocket()){
                        socket.bind(new InetSocketAddress("localhost", COMMAND_SOCKET_PORT));
                        while(true){
                            Socket client = socket.accept();
                            info("&lmRecieved command socket connection: &lb{0}", socket.getLocalSocketAddress());
                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                            socketOutput = new PrintWriter(client.getOutputStream(), true);
                            String line;
                            while(client.isConnected() && (line = in.readLine()) != null){
                                String result = line;
                                Core.app.post(() -> handleCommandString(result));
                            }
                            info("&lmLost command socket connection: &lb{0}", socket.getLocalSocketAddress());
                            socketOutput = null;
                        }
                    }
                }catch(BindException b){
                    err("Command input socket already in use. Is another instance of the server running?");
                }catch(IOException e){
                    err("Terminating socket server.");
                    e.printStackTrace();
                }
            });
            socketThread.setDaemon(true);
            socketThread.start();
        }else if(socketThread != null){
            socketThread.interrupt();
            socketThread = null;
            socketOutput = null;
        }
    }

    Gamemode getLastMode() {
        return lastMode;
    }

    Task getLastTask() {
        return lastTask;
    }

    public Boolean getInExtraRound() {
        return inExtraRound;
    }

    void setInExtraRound(Boolean state){
        inExtraRound = state;
    }

    void setLastMode(Gamemode mode){
        lastMode = mode;
    }

    int getCommandSocketPort(){
        return COMMAND_SOCKET_PORT;
    }

}
