package org.treasureboat.gradle.internal;

import java.io.File;
import java.util.List;

/**
 * Created by plotters on 12/13/16.
 */
public interface TreasureBoatLaunchContext {

//    TreasureBoatVersion getTreasureBoatVersion();

    String getScriptName();

    void setScriptName(String scriptName);

    String getEnv();

    void setEnv(String env);

    String getArgs();

    void setArgs(String args);

    File getBaseDir();

    void setBaseDir(File baseDir);

    List<File> getCompileDependencies();

    void setCompileDependencies(List<File> compileDependencies);

    List<File> getRuntimeDependencies();

    void setRuntimeDependencies(List<File> runtimeDependencies);

    List<File> getBuildDependencies();

    void setBuildDependencies(List<File> buildDependencies);

    List<File> getProvidedDependencies();

    void setProvidedDependencies(List<File> providedDependencies);

    List<File> getTestDependencies();

    void setTestDependencies(List<File> testDependencies);

    File getGrailsWorkDir();

    void setGrailsWorkDir(File grailsWorkDir);

    File getProjectWorkDir();

    void setProjectWorkDir(File projectWorkDir);

    File getClassesDir();

    void setClassesDir(File classesDir);

    File getTestClassesDir();

    void setTestClassesDir(File testClassesDir);

    File getResourcesDir();

    void setResourcesDir(File resourcesDir);

    File getProjectPluginsDir();

    void setProjectPluginsDir(File projectPluginsDir);

    boolean isPlainOutput();

    void setPlainOutput(boolean plainOutput);

    void setDependenciesExternallyConfigured(boolean dependenciesExternallyConfigured);

    boolean isDependenciesExternallyConfigured();

    void setTestReportsDir(File dir);

    File getTestReportsDir();

    void setGlobalPluginsDir(File dir);

    File getGlobalPluginsDir();

}
