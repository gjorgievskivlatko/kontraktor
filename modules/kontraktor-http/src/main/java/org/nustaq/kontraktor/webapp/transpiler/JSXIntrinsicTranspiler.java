package org.nustaq.kontraktor.webapp.transpiler;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import org.nustaq.kontraktor.Actors;
import org.nustaq.kontraktor.util.Log;
import org.nustaq.kontraktor.webapp.javascript.FileResolver;
import org.nustaq.kontraktor.webapp.npm.JNPM;
import org.nustaq.kontraktor.webapp.npm.JNPMConfig;
import org.nustaq.kontraktor.webapp.transpiler.jsx.FileWatcher;
import org.nustaq.kontraktor.webapp.transpiler.jsx.ImportSpec;
import org.nustaq.kontraktor.webapp.transpiler.jsx.JSXGenerator;
import org.nustaq.kontraktor.webapp.transpiler.jsx.NodeLibNameResolver;

import java.io.*;
import java.util.*;

/**
 * transpiles jsx without requiring babel.
 */
public class JSXIntrinsicTranspiler implements TranspilerHook {

    protected boolean dev;
    protected File jnpmNodeModulesDir;
    protected boolean autoJNPM;
    protected JNPMConfig jnpmConfig;
    protected String jnpmConfigFile;
    protected JNPMConfig jnpmConfigFileCached;
    protected List<File> readFiles;
    protected FileWatcher watcher;

    public JSXIntrinsicTranspiler(boolean dev) {
        this.dev = dev;
        this.autoJNPM = dev; // use fluent setter to turn off also for dev
    }

    @Override
    public byte[] transpile(File f) throws TranspileException {
        throw new RuntimeException("should not be called");
    }

    @Override
    public byte[] transpile(File f, FileResolver resolver, Map<String,Object> alreadyResolved) {
        byte[] bytes = processJSX(dev, f, resolver, alreadyResolved);
        return bytes;
    }

    private NodeLibNameResolver createNodeLibNameResolver(FileResolver resolver) {
        return new NodeLibNameResolver() {
            @Override
            public String getFinalLibName(File requiredIn, FileResolver res, String requireText) {
                File file = null;
                try {
                    file = findNodeModulesNearestMatch(requiredIn,requireText);
                    if ( file == null )
                        file = resolver.resolveFile(requiredIn.getParentFile(), requireText);
                    if ( file == null )
                        file = resolver.resolveFile(requiredIn.getParentFile(), requireText+".js");
                    if ( file == null )
                        file = resolver.resolveFile(requiredIn.getParentFile(), requireText+".jsx");
                    if ( file == null ) {
                        Log.Warn(this,"unable to find finalLibName for:"+requireText+" in "+requiredIn.getAbsolutePath());
                        return requireText;
                    }
                    if ( file.isDirectory() )
                        file = processNodeDir(file,resolver,new HashMap());
                    return constructLibName(file,resolver);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            public byte[] resolve(File baseDir, String name, Map<String, Object> alreadyProcessed) {
                return resolver.resolve(baseDir,name,alreadyProcessed);
            }

            @Override
            public File resolveFile(File baseDir, String name) {
                return resolver.resolveFile(baseDir,name);
            }

            @Override
            public void install(String path, byte[] resolved) {
                resolver.install(path,resolved);
            }

            @Override
            public String resolveUniquePath(File file) {
                return resolver.resolveUniquePath(file);
            }
        };
    }

    static File falseFile = new File("false");

    /**
     * return first subdirectory of nearest node path. e.. ../node_modules/react for ../node_modules/dist/lib/index.js
     * @param requiringFile
     * @param requireText
     * @return
     * @throws IOException
     */
    File findNodeModulesNearestMatch(File requiringFile, String requireText) throws IOException {
        if ( requiringFile == null )
            return null;
        if ( !requireText.startsWith(".") ) {
            File f = new File(requiringFile, "node_modules/" + requireText);
            if (f.exists())
                return new File(f.getCanonicalPath());
            f = new File(requiringFile, "node_modules/" + requireText + ".js");
            if (f.exists())
                return new File(f.getCanonicalPath());
            return findNodeModulesNearestMatch(requiringFile.getParentFile(), requireText);
        } else {
            File f = new File(requiringFile.getParentFile(),requireText);
            if ( ! f.exists() )
                f =  new File(requiringFile.getParentFile(),requireText+".js");
            if ( f.exists() )
                return new File(f.getCanonicalPath());
        }
        return null;
    }

    String findNodeSubDir(File requiringFile) throws IOException {
        if ( requiringFile == null )
            return null;
        if ( requiringFile.getParentFile() != null && requiringFile.getParentFile().getName().equals("node_modules") )
            return requiringFile.getCanonicalPath();
        else
            return findNodeSubDir(requiringFile.getParentFile());
    }

    private File processNodeDir(File file, FileResolver resolver, Map<String, Object> alreadyResolved) {
        File jfi = new File(file, "package.json");
        if ( jfi.exists() ) {
            try {
                JsonObject pkg = Json.parse(new FileReader(jfi)).asObject();
                JsonValue browser = pkg.get("browser");
                if ( browser != null ) {
                    if (browser.isBoolean() && !browser.asBoolean() ) {
                        return falseFile;
                    }
                    if ( browser.isString() ) {
//                        Log.Info(this,"package.json browser entry map to "+browser.asString());
                        return new File(file,browser.asString());
                    }
                    if ( browser.isObject() ) {
                        String nodeModuleDir = file.getCanonicalPath();
                        JsonObject members = browser.asObject();
                        members.forEach( member -> {
                            String key = "browser_" + nodeModuleDir + "_" + member.getName();
                            alreadyResolved.put(key, member.getValue());
//                            System.out.println("put browser:"+key);
//                            System.out.println("  val:"+member.getValue());
                        });
                    } else {
                        Log.Warn(this, "unrecognized 'browser' entry in package.json, " + file.getCanonicalPath());
                        return null;
                    }
                }

                String main = pkg.getString("main", null);
                if ( main != null ) {
                    if ( ! main.endsWith(".js") )
                        main = main+".js"; // omg
                    File newF = new File(file, main);
                    return newF;
                }
                File indexf = new File(file, "index.js");
                if ( indexf.exists() ) {
                    return indexf;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if ( new File(file,"index.js").exists() ) {
            return new File(file,"index.js");
        } else if ( new File(file.getParentFile(),file.getName()+".js").exists() ) {
            return new File(file.getParentFile(),file.getName()+".js");
        }
        return null;
    }

    protected byte[] processJSX(boolean dev, File f, FileResolver resolver, Map<String, Object> alreadyResolved) {
        try {
            boolean isInitialIndexJSX = "index.jsx".equals(f.getName());
            if ( isInitialIndexJSX ) {
                jnpmConfigFileCached = null;
                if ( dev ) {
                    readFiles = new ArrayList<>();
                    if ( watcher != null ) {
                        watcher.stopWatching();
                        watcher = null;
                    }
                }
            }
            if ( dev ) {
                if ( isNotInNodeModules(f) )
                    readFiles.add(f);
            }
            JSXGenerator.ParseResult result = JSXGenerator.process(f,dev,createNodeLibNameResolver(resolver),getConfig());
            List<ImportSpec> specs = result.getImports();
            byte[] res = result.getFiledata();
            if (isInitialIndexJSX) {
                alreadyResolved.put("JSXIndexStart", System.currentTimeMillis());
                ByteArrayOutputStream baos = new ByteArrayOutputStream(1_000_000);
                baos.write((getInitialShims()+"\n").getBytes("UTF-8"));
                alreadyResolved.put("JSXIndex", baos);
            }
            ByteArrayOutputStream indexBaos = (ByteArrayOutputStream) alreadyResolved.get("JSXIndex");
            if ( alreadyResolved.get("_Ignored") == null ) {
                alreadyResolved.put("_Ignored",result.getIgnoredRequires());
            }
            Set ignoredRequires = (Set) alreadyResolved.get("_Ignored");
            ignoredRequires.addAll(result.getIgnoredRequires());

            for (int i = 0; i < specs.size(); i++) {
                ImportSpec importSpec = specs.get(i);
                File redirected = resolveImportSpec(f, importSpec, resolver, alreadyResolved, ignoredRequires);
                if (redirected == null) continue;
            }
            ByteArrayOutputStream mainBao = dev ? new ByteArrayOutputStream(20_000) : indexBaos;
            if (result.generateESWrap())
                mainBao.write(generateImportPrologue(result, resolver).getBytes("UTF-8"));
            if (result.generateCommonJSWrap())
                mainBao.write(generateCommonJSPrologue(f,result, resolver).getBytes("UTF-8"));
            mainBao.write(res);
            if (result.generateESWrap())
                mainBao.write(generateImportEnd(result, resolver).getBytes("UTF-8"));
            if (result.generateCommonJSWrap())
                mainBao.write(generateCommonJSEnd(f,result, resolver).getBytes("UTF-8"));

            if ( dev ) {
                String dirName = "_node_modules";
                if (isNotInNodeModules(f)) {
                    dirName = "_appsrc";
                }
                String name = constructLibName(f, resolver) + ".transpiled";
                resolver.install("/"+dirName+"/" + name, mainBao.toByteArray());
                indexBaos.write(
                    ("document.write( '<script src=\""+dirName+"/" + name + "\"></script>');\n")
                        .getBytes("UTF-8")
                );
            }
            if (isInitialIndexJSX) {
                if ( dev ) {
                    indexBaos.write("document.write('<script>_kreporterr = true; kinitfuns.forEach( fun => fun() );</script>')\n".getBytes("UTF-8"));
                    watcher = Actors.AsActor(FileWatcher.class);
                    watcher.setFiles(readFiles);
                    watcher.startWatching();
                }
                else
                    indexBaos.write( "_kreporterr = true; kinitfuns.forEach( fun => fun() );\n".getBytes("UTF-8"));
                Long tim = (Long) alreadyResolved.get("JSXIndexStart");
                Log.Info(this, "Transpilation time:"+(System.currentTimeMillis()-tim)/1000.0);
                return indexBaos.toByteArray();
            }
            return mainBao.toByteArray();
        } catch (Exception e) {
            Log.Error(this,e);
            StringWriter out = new StringWriter();
            e.printStackTrace(new PrintWriter(out));
            try {
                return out.getBuffer().toString().getBytes("UTF-8");
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
            }
        }
        return new byte[0];
    }

    public static boolean isNotInNodeModules(File f) {
        return f.getAbsolutePath().replace('\\','/').indexOf("/node_modules/") < 0;
    }

    private File resolveImportSpec(File requiringFile, ImportSpec importSpec, FileResolver resolver, Map<String, Object> alreadyResolved, Set ignoredRequires) throws IOException {
        String from = importSpec.getFrom();
        File toReadFrom = requiringFile;
        String toReadFromName = null; // node package entry processing
        if ( importSpec.isRequire() ) {
            if (ignoredRequires.contains(importSpec.getFrom()) )
                return null;

            String canonicalF = findNodeSubDir(requiringFile);
            if ( canonicalF != null ) {
                // check for ignored requires in browser entry of package.json
                String key = "browser_" + canonicalF + "_" + from;
                JsonValue o = (JsonValue) alreadyResolved.get(key);
                if (o != null) {
                    if (o.isString()) {
                        String oldFrom = from;
                        from = o.asString();
                        Log.Info(this,"mapping package.json/browser:"+oldFrom+" to "+from);
                    } else if (o.isBoolean()) {
                        if (!o.asBoolean()) {
                            Log.Info(this,"ignoring because of package.json/browser:"+from);
                            return null;
                        }
                    } else
                        Log.Warn(this, "unrecognized browser entry in package.json:" + o + ". file:" + requiringFile.getAbsolutePath());
                } else {
//                            System.out.println("key lookup == null for browser setting :"+key);
                }
            } else {
                Log.Warn(this, "node module dir could not be resolved " + requiringFile.getAbsolutePath());
                return null;
            }
        }
        File resolvedFile;
        if (importSpec.isRequire() ) {
            resolvedFile = findNodeModulesNearestMatch(requiringFile,from);
            if ( resolvedFile != null ) {
                toReadFromName = resolvedFile.getName();
                toReadFrom = resolvedFile;
            } else {
                int debug = 1;
            }
        } else {
            resolvedFile = resolver.resolveFile(requiringFile.getParentFile(), from);
        }
        if ( resolvedFile != null && resolvedFile.isDirectory() ) {
            File indexFile = processNodeDir(resolvedFile, resolver, alreadyResolved);
            if ( indexFile == falseFile ) {
                return null;
            }
            if ( indexFile == null )
            {
                Log.Warn(this,"node directory could not be resolved to a resource :"+resolvedFile.getCanonicalPath());
                return null;
            } else {
                toReadFrom = indexFile;
                toReadFromName = indexFile.getName();
            }
        } else {
            // fixme: hasExtension
            if (!from.endsWith(".js") && !from.endsWith(".jsx") && !from.endsWith(".json")) {
                from += ".js";
            }
        }
        byte resolved[] = resolver.resolve(toReadFrom.getParentFile(), toReadFromName != null ? toReadFromName : from, alreadyResolved);
        if ( resolved == null && from.endsWith(".js") ) {
            // try jsx
            from = from.substring(0,from.length()-3)+".jsx";
            resolved = resolver.resolve(requiringFile.getParentFile(), from, alreadyResolved);
        }
        if ( resolved != null ) {
            if ( resolved.length > 0 ) {
                // need re-resolve as extension might have changed
                resolvedFile = resolver.resolveFile(toReadFrom.getParentFile(),toReadFromName != null ? toReadFromName : from);
                String name = null;
                if ( resolvedFile.getName().endsWith(".json") ) {
                    name = constructLibName(resolvedFile, resolver) + ".json";
                    ByteArrayOutputStream jsonBao = new ByteArrayOutputStream(resolved.length+100);
                    jsonBao.write("(function(exports, require, module, __filename, __dirname) { module.exports = \n".getBytes("UTF-8"));
                    jsonBao.write(resolved);
                    String s = constructLibName(requiringFile, resolver);
                    jsonBao.write(
                        ("})( kgetModule('"+s+"').exports, krequire, kgetModule('"+s+"'), '', '' );").getBytes("UTF-8"));
                    resolver.install("/debug/" + name, jsonBao.toByteArray());
                }
            }
        }
        else {
            if ( autoJNPM && jnpmNodeModulesDir != null ) {
                String required = importSpec.getFrom();
                int i = required.indexOf("/");
                if ( i >= 0 ) {
                    required = required.substring(0,i);
                }
                // single file can't be a node module
                if ( required.indexOf(".") < 0 ) {
                    JNPMConfig config = getConfig();
                    Log.Info(this, importSpec.getFrom() + " not found. installing .. '" + required+"'");
                    try {
                        JNPM.InstallResult await = JNPM.Install(required, null, jnpmNodeModulesDir, config).await(60_000);
                        if ( await == JNPM.InstallResult.INSTALLED )
                            return resolveImportSpec(requiringFile, importSpec, resolver, alreadyResolved, ignoredRequires);
                    } catch (Throwable kt) {
                        kt.printStackTrace();
                        Log.Error(this,"jnpm install timed out. Check Proxy JVM settings, internet connectivity or just retry");
                    }
                }
            }
            Log.Warn(this, importSpec.getFrom() + " not found. requiredBy:" + requiringFile.getCanonicalPath());
        }
        return requiringFile;
    }

    protected JNPMConfig getConfig() {
        return jnpmConfig != null ?
            jnpmConfig
            : (jnpmConfigFile != null ?
            (jnpmConfigFileCached != null ? jnpmConfigFileCached : (jnpmConfigFileCached = JNPMConfig.read(jnpmConfigFile)) )
            : new JNPMConfig()
        );
    }

    protected String generateCommonJSPrologue(File f, JSXGenerator.ParseResult result, FileResolver resolver ) {
        return "(function(exports, require, module, __filename, __dirname) {\n";
    }

    protected String generateCommonJSEnd(File f, JSXGenerator.ParseResult result, FileResolver resolver) {
        String s = constructLibName(f, resolver);
        return "\n})( kgetModule('"+s+"').exports, krequire, kgetModule('"+s+"'), '', '' );";
    }

    protected String generateImportEnd(JSXGenerator.ParseResult result, FileResolver resolver) {
        String s = "\n\n\n//generated by jsxtranspiler\n";
        String exportObject = "kimports['" + constructLibName(result.getFile(), resolver)+"']";
        s += exportObject+" = {};";
        for (int i = 0; i < result.getGlobals().size(); i++) {
            String gl = result.getGlobals().get(i);
            s+=exportObject+"."+gl+" = "+gl+";";
            if ( gl.equals(result.getDefaultExport())) {
                s+=exportObject+".__kdefault__ = "+gl+";";
            }
        }
        return s+"});\n";
    }

    protected String generateImportPrologue(JSXGenerator.ParseResult result, FileResolver resolver) {
        String s = "";
        s += "(new function() {\n";
        List<ImportSpec> imports = result.getImports();
        // declaration
        for (int i = 0; i < imports.size(); i++) {
            ImportSpec spec = imports.get(i);
            String libname = createNodeLibNameResolver(resolver).getFinalLibName(result.getFile(),resolver,spec.getFrom());
            if ( spec.getAlias() != null ) {
                s+="let "+spec.getAlias()+"=null;";
            }
            for (int j = 0; j < spec.getAliases().size(); j++) {
                String alias = spec.getAliases().get(j);
                s+="let "+alias+"=null;";
            }
        }
        s+="\n  const _initmods = () => {\n";
        for (int i = 0; i < imports.size(); i++) {
            ImportSpec spec = imports.get(i);
            String libname = createNodeLibNameResolver(resolver).getFinalLibName(result.getFile(),resolver,spec.getFrom());
            if ( spec.getAlias() != null ) {
                s+="    "+spec.getAlias()+" = _kresolve('"+libname+"');\n";
            }
            for (int j = 0; j < spec.getAliases().size(); j++) {
                String alias = spec.getAliases().get(j);
                s+="    "+alias+" = _kresolve('"+libname+"', '"+spec.getComponents().get(j)+"');\n";
            }
        }
        s += "  };\n";
        s += "  kaddinit(_initmods);\n\n";
        return s;
    }

    protected String constructLibName(File f, FileResolver resolver) {
        String unique = resolver.resolveUniquePath(f);
        if (unique.startsWith("/"))
            unique = unique.substring(1);
        String name = unique;
        if ( name.endsWith(".js") )
            name = name.substring(0,name.length()-3);
        if ( name.endsWith(".jsx") )
            name = name.substring(0,name.length()-4);
        if ( name.endsWith(".json") )
            name = name.substring(0,name.length()-5);
        return name;
    }

    protected String getInitialShims() {
        return
            "window.kmodules = {};\n" +
            "\n" +
            "  function kgetModule(name) {\n" +
            "    var res = kmodules[name];\n" +
            "    if ( res == null ) {\n" +
            "      kmodules[name] = { exports: {} };\n" +
            "      return kgetModule(name);\n" +
            "    } else {\n" +
            "      return res;\n" +
            "    }\n" +
            "  }\n" +
            "\n" +
            "  function krequire(name) {\n" +
            "    const res = kgetModule(name).exports;\n" +
            "    return res;\n" +
            "  }\n" +
            "\n"+
            "window.klibmap = window.klibmap || {};\nwindow.kimports = window.kimports || {};\n"+
            "window._sprd = function (obj) {\n" +
            "  const copy = Object.assign({},obj);\n" +
            "  Object.keys(obj).forEach( key => {\n" +
            "    if ( key.indexOf(\"...\") == 0 ) {\n" +
            "      const ins = obj[key];\n" +
            "      if ( typeof ins != 'undefined') {\n" +
            "        Object.keys(ins).forEach( ikey => {\n" +
            "          if ( typeof ins[ikey] != 'undefined')\n" +
            "          {\n" +
            "            obj[ikey] = ins[ikey];\n" +
            "          }\n" +
            "        });\n" +
            "        delete obj[key];\n" +
            "      }\n" +
            "    } else {\n" +
            "      obj[key] = copy[key]; // overwrite original value\n" +
            "    }\n" +
            "  });\n" +
            "  return obj;\n" +
            "};\n" +
            "var _kreporterr = false;"+
            "window._kresolve = function (libname,identifier) {\n" +
            "  var res = klibmap[libname] ? klibmap[libname]() : (window.kimports[libname] ? window.kimports[libname] : null);\n" +
            "  if ( identifier && res) res = res[identifier];\n"+
            "  if ( ! res ) {\n" +
            "    if ( !identifier)\n"+
            "        res = kmodules[libname] ? kmodules[libname].exports : null;\n" +
            "    else\n"+
            "        res = kmodules[libname] ? kmodules[libname].exports[identifier] : null;\n" +
            "  }\n" +
            "  if ( ! res ) {\n" +
            "    if (_kreporterr) console.error(\"unable to resolve \"+identifier+\" in klibmap['\"+libname+\"'] \")\n" +
            "  }\n" +
            "  else if (!identifier) {"+
            "    var res1 = res.__esModule ? res.default:res;\n" +
            "    return res1.__kdefault__ ? res1.__kdefault__ : res1;\n"+
            "  }"+
            "  return res;\n" +
            "};\n" +
            "window.module = {}; \n" +
            "const kinitfuns = [];\n"+
            "function kaddinit(fun) { fun(); kinitfuns.push(fun); }\n"+
            (dev ? "window.process = { env: {} };" : "window.process = { env: { 'NODE_ENV' : 'production' } };")+
        "\n";
    }

    public JSXIntrinsicTranspiler nodeModulesDir(File jnpmNodeModulesDir) {
        this.jnpmNodeModulesDir = jnpmNodeModulesDir;
        return this;
    }

    public JSXIntrinsicTranspiler configureJNPM(String nodeModulesDir, String pathToJNPMConfigKsonFile) {
        this.jnpmNodeModulesDir = new File(nodeModulesDir);
        this.jnpmConfigFile = pathToJNPMConfigKsonFile;
        return this;
    }

    public JSXIntrinsicTranspiler configureJNPM(String nodeModulesDir, JNPMConfig config) {
        this.jnpmNodeModulesDir = new File(nodeModulesDir);
        this.jnpmConfig = config;
        return this;
    }

    /**
     * automatically import unknown modules via jnpm
     *
     * @param b
     * @return
     */
    public JSXIntrinsicTranspiler autoJNPM(boolean b) {
        this.autoJNPM = b;
        return this;
    }

}
