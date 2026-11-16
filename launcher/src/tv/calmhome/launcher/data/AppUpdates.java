package tv.calmhome.launcher.data;

/**
 * Where a tile's "update available" dot comes from. Android's PackageManager cannot see Play Store updates, and this launcher has no
 * authorised update source, so the shipped provider is {@link #NONE}: it never reports an update, and no dot is ever shown. A real
 * source (an app store's documented API, or ZeroPulse's own download catalogue for apps it installed) plugs in here without touching
 * the tiles. Nothing here guesses.
 */
public interface AppUpdates {

    /** True only when a reliable source says {@code pkg} has an update waiting. */
    boolean updateAvailable(String pkg);

    /** The shipped provider: no authorised update source exists, so no update is ever claimed. */
    AppUpdates NONE = pkg -> false;
}
