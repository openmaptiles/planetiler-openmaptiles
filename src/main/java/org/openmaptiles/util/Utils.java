package org.openmaptiles.util;

import com.onthegomap.planetiler.util.Parse;
import java.util.Map;

/**
 * Common utilities for working with data and the OpenMapTiles schema in {@code layers} implementations.
 */
public class Utils {

  public static <T> T coalesce(T a, T b) {
    return a != null ? a : b;
  }

  public static <T> T coalesce(T a, T b, T c) {
    return a != null ? a : b != null ? b : c;
  }

  public static <T> T coalesce(T a, T b, T c, T d) {
    return a != null ? a : b != null ? b : c != null ? c : d;
  }

  public static <T> T coalesce(T a, T b, T c, T d, T e) {
    return a != null ? a : b != null ? b : c != null ? c : d != null ? d : e;
  }

  public static <T> T coalesce(T a, T b, T c, T d, T e, T f) {
    return a != null ? a : b != null ? b : c != null ? c : d != null ? d : e != null ? e : f;
  }

  /** Boxes {@code a} into an {@link Integer}, or {@code null} if {@code a} is {@code nullValue}. */
  public static Long nullIfLong(long a, long nullValue) {
    return a == nullValue ? null : a;
  }

  /** Boxes {@code a} into a {@link Long}, or {@code null} if {@code a} is {@code nullValue}. */
  public static Integer nullIfInt(int a, int nullValue) {
    return a == nullValue ? null : a;
  }

  /** Returns {@code a}, or null if {@code a} is "". */
  public static String nullIfEmpty(String a) {
    return (a == null || a.isEmpty()) ? null : a;
  }

  /** Returns true if {@code a} is null, or its {@link Object#toString()} value is "". */
  public static boolean nullOrEmpty(Object a) {
    return a == null || a.toString().isEmpty();
  }

  /** Returns a map with {@code ele} (meters) and {ele_ft} attributes from an elevation in meters. */
  public static Map<String, Object> elevationTags(double meters) {
    return Map.of(
      "ele", (int) Math.round(meters),
      "ele_ft", (int) Math.round(meters * 3.2808399)
    );
  }

  /**
   * Returns a map with {@code ele} (meters) and {ele_ft} attributes from an elevation string in meters, if {@code
   * meters} can be parsed as a valid number.
   */
  public static Map<String, Object> elevationTags(String meters) {
    Double ele = Parse.meters(meters);
    return ele == null ? Map.of() : elevationTags(ele);
  }

  /** Returns "bridge" or "tunnel" string used for "brunnel" attribute by OpenMapTiles schema. */
  public static String brunnel(boolean isBridge, boolean isTunnel) {
    return brunnel(isBridge, isTunnel, false);
  }

  /** Returns "bridge" or "tunnel" or "ford" string used for "brunnel" attribute by OpenMapTiles schema. */
  public static String brunnel(boolean isBridge, boolean isTunnel, boolean isFord) {
    return isBridge ? "bridge" : isTunnel ? "tunnel" : isFord ? "ford" : null;
  }
}
