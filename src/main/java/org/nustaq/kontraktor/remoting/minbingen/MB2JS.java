package org.nustaq.kontraktor.remoting.minbingen;


import java.util.*;
import java.io.*;

// add imports you need during generation =>
import com.lukehutch.fastclasspathscanner.FastClasspathScanner;
import org.nustaq.kontraktor.annotations.GenRemote;
import org.nustaq.serialization.*;

// this header is always required to make it work. Cut & Paste this as template
public class MB2JS {

    public static void Gen( String packagesToScanColonSeparated, String outputFile ) throws Exception {
        AbstractGen gen = new AbstractGen() {
            @Override
            protected void genClzList(String outFile, ArrayList<String> finallist, GenContext ctx, HashMap<Class, List<MsgInfo>> infoMap, String templateFile) throws Exception {
                    GenClazzInfo infos[] = new GenClazzInfo[finallist.size()];
                    for (int i = 0; i < infos.length; i++) {
                        infos[i] = new GenClazzInfo( conf.getClassInfo(Class.forName(finallist.get(i))) );
                        infos[i].setMsgs(infoMap.get(infos[i].getClzInfo().getClazz()));
                        if ( infos[i] != null )
                            System.out.println("generating clz "+finallist.get(i));
                    }
                    ctx.clazzInfos = infos;
                    new MB2JS().receiveContext(ctx, new PrintStream(new FileOutputStream(outputFile)));
            }

            @Override
            public String getTemplateFileOrClazz() {
                return null;
            }
        };
        new FastClasspathScanner( packagesToScanColonSeparated.split(",") )
            .matchClassesWithAnnotation(GenRemote.class, (clazz) -> {
                try {
                    gen.addTopLevelClass(clazz.getName());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).scan();

        if (gen.clazzSet.size()>0) {
            gen.generate(outputFile);
        } else
            System.out.println("no @GenRemote classes found in given packages");
    }

    public void receiveContext(Object o, PrintStream out) throws Exception {
        // asign context
        GenContext CTX = (GenContext) o;
        for (int ii = 0; ii < CTX.clazzInfos.length; ii++) {
            // setup context
            GenClazzInfo INF = CTX.clazzInfos[ii];
            FSTClazzInfo CLZ = INF.getClzInfo();
            List<MsgInfo> MSGS = INF.getMsgs();
            FSTClazzInfo.FSTFieldInfo fi[] = CLZ.getFieldInfo();
// content begins here =>
// template line:23
            out.println(""); // template line:24
            out.print("var J" + CLZ.getClazz().getSimpleName());// template line:24
            out.println(" = function(obj) {"); // template line:25
            out.print("    this.__typeInfo = '" + (INF.isClientSide() ? CLZ.getClazz().getName() : CLZ.getClazz().getSimpleName()));// template line:25
            out.println("';"); // template line:26
            if (INF.isActor()) {// template line:26
                out.println("    this.receiverKey=obj;"); // template line:27
                out.println("    this._actorProxy = true;"); // template line:28
            }// template line:28
            out.println(""); // template line:29
            for (int i = 0; !INF.isActor() && fi != null && i < fi.length; i++) {
                if (fi[i] == null) {
                    System.out.println("" + i + " is null in class " + CLZ.getClazz().getName());
                    continue;
                }
                String fnam = fi[i].getField().getName();
                String na = "j_" + fnam;
                //na = Character.toUpperCase(na.charAt(0))+na.substring(1);
// template line:37
                out.print("    this." + na);// template line:37
                out.print(" = function() { return " + CTX.getJSTransform(fi[i]));// template line:37
                out.println("; };"); // template line:38
            } /*for*/
// template line:39
            out.println(""); // template line:40
            for (int i = 0; INF.isActor() && i < INF.getMsgs().size(); i++) {
                MsgInfo mi = INF.getMsgs().get(i);
// template line:42
                out.print("    this." + mi.getName());// template line:42
                out.print(" = function("); // template line:42
                for (int pi = 0; pi < mi.getParameters().length; pi++) {// template line:42
                    out.print("" + mi.getParameters()[pi].getName()); // template line:42
                    out.print("" + ((pi == mi.getParameters().length - 1) ? "" : ", ")); // template line:42
                } // template line:42
                out.print(")"); // template line:43

                if (!INF.isClientSide()) {// template line:43
                    out.println(" {"); // template line:44
                    out.println("        var call = MinBin.obj('call', {"); // template line:45
                    out.print("            method: '" + mi.getName());// template line:45
                    out.println("',"); // template line:46
                    out.println("            receiverKey: this.receiverKey,"); // template line:47
                    out.print("            args: MinBin.jarray(["); // template line:47
                    for (int pi = 0; pi < mi.getParameters().length; pi++) {// template line:47
                        out.println(""); // template line:48
                        out.print("                " + CTX.getJSTransform(mi.getParameters()[pi].getName(), mi.getParams()[pi]));// template line:48
                        out.print("" + ((pi < mi.getParameters().length - 1) ? "," : "")); // template line:48
                    }// template line:48
                    out.println(""); // template line:49
                    out.println("            ])"); // template line:50
                    out.println("        });"); // template line:51
                    if (mi.hasFutureResult()) { // template line:51
                        out.println("        return Kontraktor.send(call,true);"); // template line:52
                    } else {// template line:52
                        out.println("        return Kontraktor.send(call);"); // template line:53
                    } // template line:53
                    out.println("    };"); // template line:54
                } else { // template line:54
                    out.println(" { /**/ };"); // template line:55
                }/*if isClienSide else*/
            } /*for*/
            if (!INF.isActor()) {
// template line:58
                out.println(""); // template line:59
                out.println("    this.fromObj = function(obj) {"); // template line:60
                out.println("        for ( var key in obj ) {"); // template line:61
                out.println("            var setter = 'j_'.concat(key);"); // template line:62
                out.println("            if ( this.hasOwnProperty(setter) ) {"); // template line:63
                out.println("                this[key] = obj[key];"); // template line:64
                out.println("            }"); // template line:65
                out.println("        }"); // template line:66
                out.println("        return this;"); // template line:67
                out.println("    };"); // template line:68
                out.println("    if ( obj != null ) {"); // template line:69
                out.println("        this.fromObj(obj);"); // template line:70
                out.println("    }"); // template line:71
            } /*if is actor*/// template line:71
            out.println(""); // template line:72
            out.println("};"); // template line:73
            out.println(""); // template line:74
        } // loop over classes// template line:74
        out.println(""); // template line:75
        out.println(""); // template line:76
        out.println("var mbfactory = function(clzname,jsObjOrRefId) {"); // template line:77
        out.println("    switch (clzname) {"); // template line:78

        for (int ii = 0; ii < CTX.clazzInfos.length; ii++) {
            FSTClazzInfo CLZ = CTX.clazzInfos[ii].getClzInfo();
// template line:81
            out.print("        case '" + CLZ.getClazz().getSimpleName());// template line:81
            out.print("': return new J" + CLZ.getClazz().getSimpleName());// template line:81
            out.println("(jsObjOrRefId);"); // template line:82
        } // template line:82
        out.println("        default: if (!jsObjOrRefId) return { __typeInfo: clzname }; else { jsObjOrRefId.__typeInfo = clzname; return jsObjOrRefId; }"); // template line:83
        out.println("    }"); // template line:84
        out.println("};"); // template line:85
        out.println(""); // template line:86
        out.println("MinBin.installFactory(mbfactory);"); // template line:87

        // this footer is always required (to match opening braces in header
    } // method
} // class
// template line:91
