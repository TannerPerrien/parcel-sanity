
package com.tperrien.android.parcelsanity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

public class App {

    public enum Mode {
        COMMENT, SEARCHING, WRITE, READ
    }

    private static final String JAVA_EXTENSION = "java";
    
    private static final String TOKEN_COMMENT_SINGLE = "//";
    
    private static final String TOKEN_COMMENT_BLOCK_START = "/*";
    
    private static final String TOKEN_COMMENT_BLOCK_END = "*/";

    private static final String TOKEN_WRITE_TO_PARCEL = "writeToParcel";

    private static final String TOKEN_PARCEL = "Parcel";

    private static final String TOKEN_WRITE = "write";

    private static final String TOKEN_READ = "read";

    private static final String TOKEN_SUPER = "super";

    private static final String TOKEN_OPEN_BRACE = "{";

    private static final String TOKEN_CLOSE_BRACE = "}";

    private Pattern mWhitespacePattern = Pattern.compile("\\s*");
    
    private Pattern mTypeDeclarationPattern = Pattern.compile(".*(class|enum)\\s+(\\w+).*");

    private Pattern mWriteOperationPattern = Pattern.compile(".*\\.write([a-zA-Z]+)\\(.*");

    private Pattern mReadOperationPattern = Pattern.compile(".*\\.read([a-zA-Z]+)\\(.*");

    private Mode mMode = Mode.SEARCHING;

    public App(File rootDir) {
        @SuppressWarnings("unchecked")
        Collection<File> files = FileUtils.listFiles(rootDir, new String[] {
            JAVA_EXTENSION
        }, true);
        for (File f : files) {
            Map<String, List<String>> writeOrder = new HashMap<String, List<String>>();
            Map<String, List<String>> readOrder = new HashMap<String, List<String>>();
            Mode lastMode = null;
            String workingClass = null;
            String className = null;
            String lastWorkingClass = null;
            String lastClassName = null;
            int depth = 0;
            int depthTrigger = -1;
            int readDepth = -1;

            String filename = f.getName();
            String realClassName = filename.substring(0, filename.length() - JAVA_EXTENSION.length() - 1);

            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(f));
                String line = null;
                while ( (line = reader.readLine()) != null) {
                    Matcher whitespaceMather = mWhitespacePattern.matcher(line);
                    if (whitespaceMather.matches()) {
                        continue;
                    }
                    
                    if (line.startsWith(TOKEN_COMMENT_SINGLE)) {
                        continue;
                    }

                    if (line.contains(TOKEN_SUPER)) {
                        continue;
                    }

                    if (line.contains(TOKEN_OPEN_BRACE)) {
                        depth++;
                    }
                    if (line.contains(TOKEN_CLOSE_BRACE)) {
                        depth--;
                        if (depthTrigger != -1 && depthTrigger == depth) {
                            depthTrigger = -1;
                            workingClass = lastWorkingClass;
                            className = lastClassName;
                        }
                    }
                    
                    if (line.startsWith(TOKEN_COMMENT_BLOCK_START)) {
                        lastMode = mMode;
                        mMode = Mode.COMMENT;
                    }

                    switch (mMode) {
                        case COMMENT:
                            if (line.trim().endsWith(TOKEN_COMMENT_BLOCK_END)) {
                                mMode = lastMode;
                            }
                            break;
                        case SEARCHING:
                            Matcher typeDeclarationMatcher = mTypeDeclarationPattern.matcher(line);
                            if (typeDeclarationMatcher.matches()) {
                                if (line.contains(TOKEN_OPEN_BRACE)) {
                                    depthTrigger = depth - 1;
                                } else {
                                    depthTrigger = depth;
                                }
                                
                                if (workingClass != null) {
                                    lastWorkingClass = workingClass;
                                    lastClassName = className;
                                }
                                workingClass = realClassName;
                                className = realClassName;
                                String name = typeDeclarationMatcher.group(2);
                                if (!realClassName.equals(name)) {
                                    workingClass += "$" + name;
                                    className = name;
                                }
                                if (className == null) {
                                    System.out.println();
                                }
                                if (className.equals("dest") || className.equals("in")) {
                                    System.out.println();
                                }
                            } else if (line.contains(TOKEN_WRITE_TO_PARCEL)) {
                                mMode = Mode.WRITE;
                            } else if (className != null && line.contains(className) && line.contains(TOKEN_PARCEL)) {
                                mMode = Mode.READ;
                                readDepth = depth;
                            }
                            break;
                        case WRITE:
                            if (line.contains(TOKEN_WRITE)) {
                                Matcher m = mWriteOperationPattern.matcher(line);
                                if (m.matches()) {
                                    String operation = m.group(1);
                                    List<String> ops = writeOrder.get(workingClass);
                                    if (ops == null) {
                                        ops = new ArrayList<String>();
                                        writeOrder.put(workingClass, ops);
                                    }
                                    ops.add(operation);
                                }
                            } else if (line.contains(TOKEN_CLOSE_BRACE)) {
                                if (workingClass.equals("TrackGroup")) {
                                    System.out.println();
                                }
                                mMode = Mode.SEARCHING;
                            }
                            break;
                        case READ:
                            if (line.contains(TOKEN_READ)) {
                                Matcher m = mReadOperationPattern.matcher(line);
                                if (m.matches()) {
                                    String operation = m.group(1);
                                    List<String> ops = readOrder.get(workingClass);
                                    if (ops == null) {
                                        ops = new ArrayList<String>();
                                        readOrder.put(workingClass, ops);
                                    }
                                    ops.add(operation);
                                }
                            } else if (line.contains(TOKEN_CLOSE_BRACE) && depth < readDepth) {
                                mMode = Mode.SEARCHING;
                            }
                            break;
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                    }
                }
            }

            for (String key : writeOrder.keySet()) {
                List<String> writeOps = writeOrder.get(key);
                List<String> readOps = readOrder.get(key);
                if (writeOps == null) {
                    System.out.println();
                }
                if (readOps == null) {
                    System.out.println();
                }
                int max = Math.min(writeOps.size(), readOps.size());
                if (max < writeOps.size()) {
                    System.out.println("More write operations in: " + key);
                } else if (max < readOps.size()) {
                    System.out.println("More read operations in: " + key);
                }
                for (int i = 0; i < max; i++) {
                    String writeOp = writeOps.get(i);
                    String readOp = readOps.get(i);
                    if (!writeOp.equals(readOp)) {
                        System.out.println("Error in " + key + ": Write: " + TOKEN_WRITE + writeOp + " / Read: " + TOKEN_READ + readOp);
                    }
                }
            }
        }

        System.out.println("Done!");
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Missing argument to source path");
        }

        File file = new File(args[0]);
        if (!file.exists()) {
            throw new IllegalArgumentException("The supplied file path does not exist");
        }

        new App(file);
    }

}
