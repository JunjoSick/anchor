# Classes that are created by name at runtime, which the code shrinker (R8) can't see being used
# and would otherwise remove or rename.

# Room databases are created by looking up the generated "<Name>_Impl" class by name. The widget
# library (Glance) uses WorkManager, which keeps its state in such a database; without this rule
# the release build crashed on startup ("Failed to create an instance of WorkDatabase").
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# WorkManager creates its input mergers and workers from class names stored in that database.
# The widget library renders widgets inside such a worker (SessionWorker); without these the
# release build's widget stayed blank ("OverwritingInputMerger has no zero argument constructor").
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Glance creates a widget tap handler (actionRunCallback<T>) from its class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
