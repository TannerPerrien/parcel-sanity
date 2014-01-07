package com.tperrien.android.parcelablessanity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

public class App {

    public enum Mode {
        SEARCHING, WRITE, READ
    }

    private static final String JAVA_EXTENSION = "java";

    private static final String TOKEN_WRITE_TO_PARCEL = "writeToParcel";

    private static final String TOKEN_PARCEL = "Parcel";

    private static final String TOKEN_WRITE = "write";

    private static final String TOKEN_READ = "read";

    private static final String TOKEN_CLOSE_BRACE = "}";

    private Pattern mWhitespacePattern = Pattern.compile("\\s*");

    private Pattern mWriteOperationPattern = Pattern.compile(".*\\.write([a-zA-Z]+)\\(.*");

    private Pattern mReadOperationPattern = Pattern.compile(".*\\.read([a-zA-Z]+)\\(.*");

    private Mode mMode = Mode.SEARCHING;

    public App(File rootDir) {
        @SuppressWarnings("unchecked")
        Collection<File> files = FileUtils.listFiles(rootDir, new String[] { JAVA_EXTENSION }, true);
        for (File f : files) {
            List<String> writeOrder = new ArrayList<String>();
            List<String> readOrder = new ArrayList<String>();

            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(f));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    Matcher whitespaceMather = mWhitespacePattern.matcher(line);
                    if (whitespaceMather.matches()) {
                        continue;
                    }

                    switch (mMode) {
                    case SEARCHING:
                        String filename = f.getName();
                        String className = filename.substring(0, filename.length() - JAVA_EXTENSION.length() - 1);

                        if (line.contains(TOKEN_WRITE_TO_PARCEL)) {
                            mMode = Mode.WRITE;
                        } else if (line.contains(className) && line.contains(TOKEN_PARCEL)) {
                            mMode = Mode.READ;
                        }
                        break;
                    case WRITE:
                        if (line.contains(TOKEN_WRITE)) {
                            Matcher m = mWriteOperationPattern.matcher(line);
                            if (m.matches()) {
                                String operation = m.group(1);
                                writeOrder.add(operation);
                            }
                        } else if (line.contains(TOKEN_CLOSE_BRACE)) {
                            mMode = Mode.SEARCHING;
                        }
                        break;
                    case READ:
                        if (line.contains(TOKEN_READ)) {
                            Matcher m = mReadOperationPattern.matcher(line);
                            if (m.matches()) {
                                String operation = m.group(1);
                                readOrder.add(operation);
                            }
                        } else if (line.contains(TOKEN_CLOSE_BRACE)) {
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

            int max = Math.min(writeOrder.size(), readOrder.size());
            if (max < writeOrder.size()) {
                System.out.println("More write operations in: " + f.getName());
            } else if (max < readOrder.size()) {
                System.out.println("More read operations in: " + f.getName());
            }
            for (int i = 0; i < max; i++) {
                String writeOp = writeOrder.get(i);
                String readOp = readOrder.get(i);
                if (!writeOp.equals(readOp)) {
                    System.out.println("Error: Write: " + TOKEN_WRITE + writeOp + " / Read: " + TOKEN_READ + readOp);
                }
            }
            
            System.out.println("Done!");
        }
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
