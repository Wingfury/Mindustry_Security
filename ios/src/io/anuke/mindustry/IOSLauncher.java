package io.anuke.mindustry;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.iosrobovm.IOSApplication;
import com.badlogic.gdx.backends.iosrobovm.IOSApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import io.anuke.kryonet.DefaultThreadImpl;
import io.anuke.kryonet.KryoClient;
import io.anuke.kryonet.KryoServer;
import io.anuke.mindustry.core.Platform;
import io.anuke.mindustry.core.ThreadHandler;
import io.anuke.mindustry.io.SaveIO;
import io.anuke.mindustry.net.Net;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.scene.ui.TextField;
import io.anuke.ucore.scene.ui.layout.Unit;
import io.anuke.ucore.util.Bundles;
import io.anuke.ucore.util.Strings;
import org.robovm.apple.foundation.NSAutoreleasePool;
import org.robovm.apple.foundation.NSURL;
import org.robovm.apple.uikit.UIActivityViewController;
import org.robovm.apple.uikit.UIApplication;
import org.robovm.apple.uikit.UIApplicationLaunchOptions;
import org.robovm.apple.uikit.UIApplicationOpenURLOptions;

import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

import static io.anuke.mindustry.Vars.control;
import static io.anuke.mindustry.Vars.ui;
import static org.robovm.apple.foundation.NSPathUtilities.getDocumentsDirectory;

public class IOSLauncher extends IOSApplication.Delegate {
    @Override
    protected IOSApplication createApplication() {
        Net.setClientProvider(new KryoClient());
        Net.setServerProvider(new KryoServer());

        Unit.dp.addition -= 0.2f;

        Platform.instance = new Platform() {
            DateFormat format = SimpleDateFormat.getDateTimeInstance();

            @Override
            public String format(Date date) {
                return format.format(date);
            }

            @Override
            public String format(int number) {
                return NumberFormat.getIntegerInstance().format(number);
            }

            @Override
            public void addDialog(TextField field) {
                TextFieldDialogListener.add(field, 16);
            }

            @Override
            public void addDialog(TextField field, int maxLength) {
                TextFieldDialogListener.add(field, maxLength);
            }

            @Override
            public String getLocaleName(Locale locale) {
                return locale.getDisplayName(locale);
            }

            @Override
            public ThreadHandler.ThreadProvider getThreadProvider() {
                return new DefaultThreadImpl();
            }

            @Override
            public void shareFile(FileHandle file){
                FileHandle to = Gdx.files.absolute(getDocumentsDirectory()).child(file.name());
                file.copyTo(to);

                NSURL url = new NSURL(to.file());
                UIActivityViewController p = new UIActivityViewController(Collections.singletonList(url), null);

                UIApplication.getSharedApplication().getKeyWindow().getRootViewController()
                        .presentViewController(p, true, () -> io.anuke.ucore.util.Log.info("Success! Presented {0}", to));
            }
        };

        IOSApplicationConfiguration config = new IOSApplicationConfiguration();
        return new IOSApplication(new Mindustry(), config);
    }

    @Override
    public boolean openURL(UIApplication app, NSURL url, UIApplicationOpenURLOptions options) {
        System.out.println("Opened URL: " + url.getAbsoluteString());
        openURL(url);
        return false;
    }

    @Override
    public boolean didFinishLaunching(UIApplication application, UIApplicationLaunchOptions options) {
        boolean b = super.didFinishLaunching(application, options);

        if(options != null && options.has(UIApplicationLaunchOptions.Keys.URL())){
            System.out.println("Opened URL at launch: " + ((NSURL)options.get(UIApplicationLaunchOptions.Keys.URL())).getAbsoluteString());
            openURL(((NSURL)options.get(UIApplicationLaunchOptions.Keys.URL())));
        }

        return b;
    }

    void openURL(NSURL url){
        String str = url.getAbsoluteURL().getAbsoluteString().toLowerCase();
        System.err.println("STR: " + str);

        Timers.runTask(30f, () -> {
            FileHandle file = Gdx.files.absolute(url.getAbsoluteString());

            if(str.endsWith("mins")){ //open save

                if(SaveIO.isSaveValid(file)){
                    try{
                        control.getSaves().importSave(file);
                    }catch (IOException e){
                        ui.showError(Bundles.format("text.save.import.fail", Strings.parseException(e, false)));
                    }
                }else{
                    ui.showError("$text.save.import.invalid");
                }

            }else if(str.endsWith("png")){ //open map
                if(!ui.editor.isShown()){
                    ui.editor.show();
                }
                ui.editor.tryLoadMap(file);
            }
        });
    }

    public static void main(String[] argv) {
        NSAutoreleasePool pool = new NSAutoreleasePool();
        UIApplication.main(argv, null, IOSLauncher.class);
        pool.close();
    }
}