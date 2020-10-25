package org.jetbrains.bsp.bazel.install;

import ch.epfl.scala.bsp4j.BspConnectionDetails;
import com.google.common.base.Splitter;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jetbrains.bsp.bazel.common.Constants;

public class Install {
  public static final String INSTALLER_BINARY_NAME = "bazelbsp-install";
  public static final String SERVER_CLASS_NAME = "org.jetbrains.bsp.bazel.server.Server";

  private static final String DEBUGGER_SHORT_OPT = "x";
  private static final String JAVA_SHORT_OPT = "j";
  private static final String BAZEL_SHORT_OPT = "b";
  private static final String HELP_SHORT_OPT = "h";
  private static final String DIRECTORY_SHORT_OPT = "d";

  public static void main(String[] args) throws IOException {
    // This is the command line which will be used to start the BSP server. See:
    // https://build-server-protocol.github.io/docs/server-discovery.html#build-tool-commands-to-start-bsp-servers
    List<String> argv = new ArrayList<>();

    Options cliOptions = getCliOptions();
    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();
    formatter.setWidth(120);
    try {
      CommandLine cmd = parser.parse(cliOptions, args, false);
      if (cmd.hasOption(HELP_SHORT_OPT)) {
        formatter.printHelp(INSTALLER_BINARY_NAME, cliOptions);
        return;
      }
      // Add Java binary
      if (cmd.hasOption(JAVA_SHORT_OPT)) {
        argv.add(cmd.getOptionValue(JAVA_SHORT_OPT));
      } else {
        String javaHome = readSystemProperty("java.home");
        argv.add(Paths.get(javaHome).resolve("bin").resolve("java").toString());
      }
      // Add Java classpath
      argv.add("-classpath");
      String javaClassPath = readSystemProperty("java.class.path");
      Splitter.on(":").splitToList(javaClassPath).stream()
          .map(elem -> Paths.get(elem).toAbsolutePath().toString())
          .forEach(argv::add);
      // Add debugger connection
      if (cmd.hasOption(DEBUGGER_SHORT_OPT)) {
        String debuggerAddress = cmd.getOptionValue(DEBUGGER_SHORT_OPT);
        argv.add(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=" + debuggerAddress);
      }
      argv.add(SERVER_CLASS_NAME);
      // Add Bazel binary
      if (cmd.hasOption(BAZEL_SHORT_OPT)) {
        argv.add(cmd.getOptionValue(BAZEL_SHORT_OPT));
      } else {
        argv.add(findOnPath("bazel"));
      }

      BspConnectionDetails details =
          new BspConnectionDetails(
              Constants.NAME,
              argv,
              Constants.VERSION,
              Constants.BSP_VERSION,
              Constants.SUPPORTED_LANGUAGES);

      Path rootDir =
          cmd.hasOption(DIRECTORY_SHORT_OPT)
              ? Paths.get(cmd.getOptionValue(DIRECTORY_SHORT_OPT))
              : Paths.get("");

      Path bazelbspDir = rootDir.resolve(".bazelbsp");
      Files.createDirectories(bazelbspDir);
      Path aspectsFile = bazelbspDir.resolve(Constants.ASPECTS_FILE_NAME);
      Files.copy(
          Install.class.getResourceAsStream(Constants.ASPECTS_FILE_NAME),
          aspectsFile,
          StandardCopyOption.REPLACE_EXISTING);
      // Create an empty BUILD file, overwrite if exists
      Files.newByteChannel(
          bazelbspDir.resolve("BUILD"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

      Path bspDir = rootDir.resolve(".bsp");
      Files.createDirectories(bspDir);
      Files.write(
          bspDir.resolve("bazelbsp.json"),
          new GsonBuilder()
              .setPrettyPrinting()
              .create()
              .toJson(details)
              .getBytes(StandardCharsets.UTF_8));
    } catch (ParseException e) {
      PrintWriter writer = new PrintWriter(System.out);
      formatter.printUsage(writer, 120, INSTALLER_BINARY_NAME, cliOptions);
      System.exit(1);
    } catch (NoSuchElementException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }

  private static Options getCliOptions() {
    Options cliOptions = new Options();

    Option java =
        Option.builder(JAVA_SHORT_OPT)
            .longOpt("java")
            .hasArg()
            .argName("path")
            .desc("Use provided Java executable to run the BSP server")
            .build();

    cliOptions.addOption(java);

    Option bazel =
        Option.builder(BAZEL_SHORT_OPT)
            .longOpt("bazel")
            .hasArg()
            .argName("path")
            .desc("Make the BSP server use this Bazel executable")
            .build();

    cliOptions.addOption(bazel);

    Option debug =
        Option.builder(DEBUGGER_SHORT_OPT)
            .longOpt("debugger")
            .hasArg()
            .argName("address (e.g. '127.0.0.1:8000'")
            .desc("Allow BSP server debugging")
            .build();

    cliOptions.addOption(debug);

    Option directory =
        Option.builder(DIRECTORY_SHORT_OPT)
            .longOpt("directory")
            .hasArg()
            .argName("path")
            .desc(
                "Path to directory where bazelbsp server should be setup. "
                    + "Current directory will be used by default")
            .build();

    cliOptions.addOption(directory);

    Option help = Option.builder(HELP_SHORT_OPT).longOpt("help").desc("Show help").build();

    cliOptions.addOption(help);

    return cliOptions;
  }

  private static String findOnPath(String bin) {
    List<String> pathElements = Splitter.on(File.pathSeparator).splitToList(System.getenv("PATH"));
    for (String pathElement : pathElements) {
      File maybePath = new File(pathElement, bin);
      if (maybePath.canExecute()) {
        return maybePath.toString();
      }
    }
    throw new NoSuchElementException("Could not find " + bin + " on your PATH");
  }

  private static String readSystemProperty(String name) {
    String property = System.getProperty(name);
    if (property == null) {
      throw new NoSuchElementException("Could not read " + name + " system property");
    }
    return property;
  }
}
