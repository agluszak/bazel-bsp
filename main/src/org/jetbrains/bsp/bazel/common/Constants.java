package org.jetbrains.bsp.bazel.common;

import com.google.common.collect.ImmutableList;
import java.util.List;

public class Constants {
  public static final String NAME = "bazelbsp";
  public static final String VERSION = "0.0.0";
  public static final String BSP_VERSION = "2.0.0";
  public static final List<String> SUPPORTED_LANGUAGES =
      ImmutableList.of("scala", "java", "kotlin");
  public static final String ASPECTS_FILE_NAME = "aspects.bzl";
}
