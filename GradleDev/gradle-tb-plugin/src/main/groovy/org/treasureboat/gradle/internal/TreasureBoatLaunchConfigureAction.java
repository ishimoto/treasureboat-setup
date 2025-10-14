package org.treasureboat.gradle.internal;

import org.gradle.api.Action;
import org.gradle.process.JavaExecSpec;

import java.io.*;

/**
 * Created by plotters on 12/13/16.
 */
public class TreasureBoatLaunchConfigureAction implements Action<JavaExecSpec> {

    private final TreasureBoatLaunchContext launchContext;
    private final File contextDestination;

    public TreasureBoatLaunchConfigureAction(TreasureBoatLaunchContext launchContext, File contextDestination) {
        this.launchContext = launchContext;
        this.contextDestination = contextDestination;
    }

    @Override
    public void execute(JavaExecSpec javaExec) {

        OutputStream fileOut = null;
        ObjectOutputStream oos = null;
        try {
            fileOut = new FileOutputStream(contextDestination);
            oos = new ObjectOutputStream(fileOut);
            oos.writeObject(launchContext);

            javaExec.setWorkingDir(launchContext.getBaseDir());
            // TODO FIXME
//            File launcherJar = findJarFile(Main.class);
//            javaExec.classpath(launcherJar);
//            javaExec.setMain(Main.class.getName());
            javaExec.args(contextDestination.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (oos != null) {
                    oos.close();
                }
                if (fileOut != null) {
                    fileOut.close();
                }
            } catch (IOException ignore) {

            }
        }

    }

    private File findJarFile(Class targetClass) {
        String absolutePath = targetClass.getResource('/' + targetClass.getName().replace(".", "/") + ".class").getPath();
        String jarPath = absolutePath.substring("file:".length(), absolutePath.lastIndexOf("!"));
        return new File(jarPath);
    }
}
