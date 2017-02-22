/*
 * Copyright 2016 Herman Cheung
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package hkhc.electricspock;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;
import org.robolectric.annotation.Config;
import org.robolectric.internal.InstrumentingClassLoaderFactory;
import org.robolectric.internal.SdkConfig;
import org.robolectric.internal.SdkEnvironment;
import org.robolectric.internal.bytecode.InstrumentationConfiguration;
import org.robolectric.internal.dependency.DependencyResolver;
import org.robolectric.manifest.AndroidManifest;
import org.spockframework.runtime.Sputnik;
import org.spockframework.runtime.model.SpecInfo;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.Properties;

import hkhc.electricspock.internal.AndroidManifestFactory;
import hkhc.electricspock.internal.ConfigFactory;
import hkhc.electricspock.internal.DependencyResolverFactory;
import hkhc.electricspock.internal.ShadowMaker;
import hkhc.electricspock.internal.ElectricSpockInterceptor;
import spock.lang.Title;

/**
 * Created by herman on 27/12/2016.
 * Test Runner
 */

public class ElectricSputnik extends Runner implements Filterable, Sortable {

    private Object sputnik;

    static {
        new SecureRandom(); // this starts up the Poller SunPKCS11-Darwin thread early, outside of any Robolectric classloader
    }

    public ElectricSputnik(Class<?> testClass) {

        DependencyResolverFactory dependencyResolverFactory = new DependencyResolverFactory();

        checkRobolectricVersion();

        AndroidManifestFactory androidManifestFactory = new AndroidManifestFactory();

        Config config = (new ConfigFactory()).getConfig(testClass);
        AndroidManifest appManifest = androidManifestFactory.getAppManifest(config);
        InstrumentingClassLoaderFactory instrumentingClassLoaderFactory =
                new InstrumentingClassLoaderFactory(
                        createClassLoaderConfig(),
                        dependencyResolverFactory.getJarResolver());
        SdkEnvironment sdkEnvironment =
                instrumentingClassLoaderFactory.getSdkEnvironment(
                        new SdkConfig(androidManifestFactory.pickSdkVersion(config, appManifest)));


        (new ShadowMaker()).configureShadows(sdkEnvironment, config);

        Class bootstrappedTestClass = sdkEnvironment.bootstrappedClass(testClass);

        // Since we have bootstrappedClass we may properly initialize

        try {

            this.sputnik = sdkEnvironment
                    .bootstrappedClass(Sputnik.class)
                    .getConstructor(Class.class)
                    .newInstance(bootstrappedTestClass);


        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // let's manually add our initializers

        for(Method method : sputnik.getClass().getDeclaredMethods()) {
            if(method.getName().equals("getSpec")) {
                method.setAccessible(true);
                try {
                    Object spec = method.invoke(sputnik);

                    // Interceptor registers on construction
                    sdkEnvironment
                            .bootstrappedClass(ElectricSpockInterceptor.class)
                            .getConstructor(
                                    sdkEnvironment.bootstrappedClass(SpecInfo.class),
                                    SdkEnvironment.class,
                                    Config.class,
                                    AndroidManifest.class,
                                    DependencyResolverFactory.class
                            )
                            .newInstance(spec, sdkEnvironment, config, appManifest, dependencyResolverFactory);

                } catch (IllegalAccessException | InstantiationException |
                        NoSuchMethodException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    private String getCurrentRobolectricVersion() {

        try {
            Properties prop = new Properties();
            prop.load(getClass().getClassLoader().getResourceAsStream("robolectric-version.properties"));
            return prop.getProperty("robolectric.version");
        }
        catch (IOException e) {
            return "Unknown";
        }


    }

    private void checkRobolectricVersion() {

        String ver = getCurrentRobolectricVersion();
        if (!(ver.equals("3.2") ||
                ver.indexOf("3.2.")==0 ||
                ver.indexOf("3.2-")==0))
            throw new RuntimeException("This version of ElectricSpock supports Robolectric 3.2 only");
    }

    private InstrumentationConfiguration createClassLoaderConfig() {
        return InstrumentationConfiguration.newBuilder()
                .doNotAcquireClass(DependencyResolver.class.getName())
                .doNotAcquireClass(DependencyResolverFactory.class.getName())
                .build();
    }

    public Description getDescription() {

        Description originalDesc = ((Runner) sputnik).getDescription();

        Class<?> testClass = originalDesc.getTestClass();
        String title = null;
        Annotation[] annotations = null;
        if (testClass!=null) {
            annotations = testClass.getAnnotations();
            for (Annotation a : annotations) {
                if (a instanceof Title) {
                    title = ((Title) a).value();
                    break;
                }
            }
        }


        Description overridedDesc = Description.createSuiteDescription(
                title==null ? testClass.getName() : title
        );
        for(Description d : originalDesc.getChildren()) {
            overridedDesc.addChild(d);
        }
        return overridedDesc;

    }

    public void run(RunNotifier notifier) {
        ((Runner) sputnik).run(notifier);
    }

    public void filter(Filter filter) throws NoTestsRemainException {
        ((Filterable) sputnik).filter(filter);
    }

    public void sort(Sorter sorter) {
        ((Sortable) sputnik).sort(sorter);
    }

}
