package org.treasureboat.gradle

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecResult
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.DefaultJavaForkOptions
import org.gradle.process.internal.ExecException
import org.gradle.process.internal.JavaExecAction
import org.treasureboat.gradle.internal.TreasureBoatLaunchConfigureAction

/**
 * Created by plotters on 12/13/16.
 */
class TreasureboatTask extends DefaultTask {

    static public final TB_GROUP = 'treasureboat'
    @Optional @InputFiles FileCollection providedClasspath
    @Optional @InputFiles FileCollection compileClasspath
    @Optional @InputFiles FileCollection runtimeClasspath
    @Optional @InputFiles FileCollection testClasspath

    @InputFiles FileCollection bootstrapClasspath

    boolean useRuntimeClasspathForBootstrap

    JavaForkOptions jvmOptions
    SourceSetContainer sourceSets

    private projectDir
    private projectWorkDir
    private boolean pluginProject

    boolean forwardStdIn
    boolean captureOutputToInfo

    TreasureboatTask() {
        this.jvmOptions = new DefaultJavaForkOptions(getServices().get(FileResolver))
        command = name
        group = TB_GROUP
    }

    @Input
    File getProjectDir() {
        projectDir == null ? null : project.file(projectDir)
    }

    @Input
    File getProjectWorkDir() {
        projectWorkDir == null ? null : project.file(projectWorkDir)
    }

    File getGrailsHome() {
        grailsHome == null ? null : project.file(grailsHome)
    }

    JavaForkOptions getJvmOptions() {
        return jvmOptions
    }

    void jvmOptions(Action<JavaForkOptions> configure) {
        project.configure(jvmOptions, { configure.execute(it) })
    }

    @TaskAction
    def executeCommand() {

        def launchContext = createLaunchContext()
        def file = new File(getTemporaryDir(), "launch.context")

        def launcher = new TreasureBoatLaunchConfigureAction(launchContext, file)

        // Capture output and only display to console in error conditions
        // if capture is enabled and info logging is not enabled.
        def capture = captureOutputToInfo && !logger.infoEnabled
        OutputStream out = capture ? new ByteArrayOutputStream() : System.out
        OutputStream err = capture ? new ByteArrayOutputStream() : System.err

        ExecResult result = project.javaexec {
            JavaExecAction action = delegate
            action.ignoreExitValue = true
            getJvmOptions().copyTo(action)
            if (forwardStdIn) {
                action.standardInput = System.in
            }
            action.standardOutput = out
            action.errorOutput = err
            launcher.execute(action)
        }

        try {
            checkExitValue(result)
        } catch (ExecException e) {
            if (capture) {
                if (out instanceof ByteArrayOutputStream) {
                    out.writeTo(System.out)
                }
                if (err instanceof ByteArrayOutputStream) {
                    err.writeTo(System.err)
                }
            }
            throw e
        }
    }

    protected void checkExitValue(ExecResult result) {
        result.rethrowFailure()
        result.assertNormalExitValue()
    }


}
