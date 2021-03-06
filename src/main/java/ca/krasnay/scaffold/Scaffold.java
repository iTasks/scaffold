/* Copyright 2015 John Krasnay
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
 */
package ca.krasnay.scaffold;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.LocalDate;

import ca.krasnay.scaffold.io.FileSink;
import ca.krasnay.scaffold.io.IOUtils;
import ca.krasnay.scaffold.io.StringSource;

public class Scaffold {

    private Class<?> entityClass;

    private File targetDir;

    private File templateDir;

    private TemplateProcessor templateProcessor;

    /**
     * Returns the filesystem directory that contains the given class. Throws
     * an exception if this cannot be determined (e.g. if the class is in
     * a JAR file).
     */
    public static File getPackageDir(Class<?> clazz) {

        URL url = clazz.getResource(clazz.getSimpleName() + ".class");

        if (!"file".equals(url.getProtocol())) {
            throw new RuntimeException("Can't find package directory for class " + clazz.getName() + ". Cannot run Scaffold from a JAR.");
        }

        return new File(url.getPath()).getParentFile();

    }

    /**
     * Returns the directory of the source file for the given class, assuming
     * this current project is a Maven project.
     */
    public static File getMavenJavaDir(Class<?> clazz) {
        return new File(getPackageDir(clazz).toString().replace("target/classes", "src/main/java"));
    }

    /**
     * Returns the resources directory corresponding to the given class,
     * assuming this current project is a Maven project.
     */
    public static File getMavenResourcesDir(Class<?> clazz) {
        return new File(getPackageDir(clazz).toString().replace("target/classes", "src/main/resources"));
    }

    public Scaffold(Class<?> entityClass) {

        this.entityClass = entityClass;

        templateDir = getPackageDir(getClass());
        targetDir = getMavenJavaDir(entityClass);
        templateProcessor = new VelocityTemplateProcessor();
    }

    /**
     * Returns the entity class being scaffolded.
     */
    public Class<?> getEntityClass() {
        return entityClass;
    }

    /**
     * Returns map of fields to be merged into the template. Subclasses may
     * override this to return their own map, but should in general call this
     * base class method and augment and return the default map.
     */
    public Map<String, Object> getMergeFields() {

        Map<String, Object> map = new LinkedHashMap<String, Object>();

        String packageName = entityClass.getPackage().getName();
        String entityName = entityClass.getSimpleName();

        List<Field> fields = new ArrayList<Field>();
        for (Field field : entityClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                fields.add(field);
            }
        }
        map.put("package", packageName);
        map.put("Entity", entityName);
        map.put("entity", entityName.substring(0, 1).toLowerCase() + entityName.substring(1));
        map.put("fields", fields);
        map.put("date", new LocalDate().toString());
        map.put("scaffold", this);

        return map;

    }

    /**
     * Renders the given template to the given output file. The template is
     * read from templateDir and the target file is written to targetDir.
     *
     * @param templateName
     *            Name of the template, relative to the template directory.
     * @param outFileName
     *            Name of the output file, relative to the target directory.
     *            This name itself is a template, so you can specify something
     *            like "${Entity}Dao.java".
     */
    public Scaffold render(String templateName, String outFileName) {

        Map<String, Object> mergeFields = getMergeFields();

        File outFile = new File(targetDir, templateProcessor.process(outFileName, mergeFields, false));

        if (outFile.exists()) {
            System.out.println("Skipping " + outFile + ": already exists");
        } else {

            System.out.println("Creating " + outFile);

            // Create any missing directories
            outFile.getParentFile().mkdirs();

            String template = IOUtils.toStringUtf8(new File(templateDir, templateProcessor.process(templateName, mergeFields, false)));
            String output = templateProcessor.process(template, mergeFields, false);
            IOUtils.copy(new StringSource(output), new FileSink(outFile));

        }

        return this;
    }

    /**
     * Sets the target directory into which to render templates. By default,
     * templates are rendered into the package directory of the entity class.
     */
    public Scaffold setTargetDir(File targetDir) {
        this.targetDir = targetDir;
        return this;
    }

}
