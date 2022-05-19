package com.onthegomap.planetiler.openmaptiles;

import com.onthegomap.planetiler.ForwardingProfile;

/** Interface for all vector tile layer implementations that {@link OpenMapTilesProfile} delegates to. */
public interface Layer extends
  ForwardingProfile.Handler,
  ForwardingProfile.HandlerForLayer {}
