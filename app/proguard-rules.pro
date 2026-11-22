# The widgets update through WorkManager, and the one Glance brings (2.7.1) keeps the classes it
# makes by name but not their constructors, which R8 then removes. Room makes WorkManager's
# database the same way.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.work.InputMerger { <init>(); }
