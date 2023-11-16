package org.openmaptiles.util;

import com.onthegomap.planetiler.util.Parse;
import java.util.Map;

/**
 * Common utilities for working with data and the OpenMapTiles schema in {@code layers} implementations.
 */
public class Utils {

  private static final double LOG2 = Math.log(2);

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

  /**
   * Calculate minzoom for a feature with given length based on threshold: if a feature's length is least
   * {@code 1 / (2^threshold)} of a tile at certain zoom level, it will be shown at that and higher zoom levels, e.g.
   * that particular zoom level is minzoom.
   * <p>
   * {@code threshold} is calculated in such way to avoid calculating log(2) os it during the runtime. Use 1 for 1/2, 2
   * for 1/4, 3 for 1/8, etc.
   * 
   * @param length    length of the feature
   * @param threshold threshold
   * @return minzoom for a feature with given length and given threshold
   */
  public static int getMinZoomForLength(double length, double threshold) {
    // Say threshold is 1/8 (threshold variable = 8) of tile size, hence ...
    // ... from pixels to world coord, for say Z14, the minimum length is:
    //  PORTION_OF_TILE_SIDE = (256d / 8) / Math.pow(2d, 14d + 8d);
    // ... and then minimum length for some lower zoom:
    //  PORTION_OF_TILE_SIDE * Math.pow(2, 14 - zoom);
    // all this then reversed and simplified to:
    double zoom = -(Math.log(length) / LOG2) - threshold;

    // Say Z13.01 means bellow threshold, Z13.00 is exactly threshold, Z12.99 is over threshold,
    // hence Z13.01 and Z13.00 will be rounded to Z14 and Z12.99 to Z13 (e.g. `floor() + 1`).
    // And to accommodate for some precision errors (observed for Z9-Z11) we do also `- 0.1e-10`.
    return (int) Math.floor(zoom - 0.1e-10) + 1;
  }

  /**
   * Same as {@link #getMinZoomForLength(double, double)} but with result within the given minimum and maximim.
   * 
   * @param length    length of the feature
   * @param threshold threshold
   * @param min       clip the result to this value if lower
   * @param max       clip the result to this value if higher
   * @return minzoom for a feature with given length and given threshold clipped to not exceed given minimum and maximum
   */
  public static int getClippedMinZoomForLength(double length, double threshold, int min, int max) {
    return Math.clamp(getMinZoomForLength(length, threshold), min, max);
  }
}
