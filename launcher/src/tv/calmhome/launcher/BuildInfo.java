package tv.calmhome.launcher;

/** Build time and git commit of this build, shown on the About screen (values set by launcher/build.gradle). */
public final class BuildInfo {
    public static final String BUILD = BuildConfig.ZP_BUILD;
    public static final String COMMIT = BuildConfig.ZP_COMMIT;

    private BuildInfo() { }
}
