# App-specific R8 rules for the release build.
#
# AGP 8.x runs R8 in full mode. Most of what this app needs is already shipped as consumer rules
# inside the libraries themselves, and was checked in the artifacts of the exact versions this
# build resolves (not assumed). Those are deliberately NOT repeated here:
#
#   Room 2.6.1 (room-runtime proguard.txt)
#     -keep class * extends androidx.room.RoomDatabase
#     Covers PokerDatabase and the generated PokerDatabase_Impl, which Room finds by name.
#     DAO *_Impl classes, @Entity classes and @TypeConverters are called directly from generated
#     code (room.generateKotlin), with no reflection, so they need no rule of their own.
#
#   Hilt / Dagger 2.52 (hilt-android proguard.txt, dagger META-INF/com.android.tools/r8/r8.pro)
#     Keeps @EntryPoint / @ComponentEntryPoint / @GeneratedEntryPoint types, @KeepFieldType fields,
#     and uses -identifiernamestring so the @HiltViewModel class-name keys follow renaming.
#     Hilt_PokerTrackerApp is referenced directly by the bytecode transform; PokerTrackerApp and
#     MainActivity are kept by the rules aapt2 generates from AndroidManifest.xml.
#
#   kotlinx-coroutines 1.9.0 (META-INF/com.android.tools/r8*/coroutines.pro)
#     Keeps AtomicFieldUpdater volatile fields, kotlin.coroutines.SafeContinuation and the Android
#     main dispatcher factory. Continuation needs nothing further. The app declares no
#     CoroutineExceptionHandler of its own.
#
#   Play Billing 9.1.0 (billing proguard.txt)
#     Keeps the names of ProxyBillingActivity / ProxyBillingActivityV2 (declared in the library's
#     manifest) and the fields of its internal protobuf classes. Its com.android.vending.billing.**
#     keep is a leftover: 9.1.0 has no classes in that package. The public API the app uses - BillingClient,
#     Purchase, ProductDetails, BillingResult and the params builders - is called directly from
#     BillingManager, never by reflection, and Purchase / ProductDetails parse Play's JSON with
#     string keys inside the library. R8 renaming them consistently is safe, so no blanket
#     -keep class com.android.billingclient.api.** is added; it would only keep dead code.
#
#   Compose 1.9.2 (runtime / ui proguard.txt), Navigation 2.9.8, Lifecycle 2.10.0
#     Ship their own rules. Navigation routes are plain strings, not @Serializable classes.
#
#   kotlinx.serialization 1.7.3 (core jar, META-INF/com.android.tools/r8/kotlinx-serialization-*.pro)
#     Used only for the game handoff (domain/transfer/GameTransfer.kt). The library's rules keep
#     the generated serializers of @Serializable classes. GameTransfer also calls
#     GameTransfer.serializer() directly rather than looking it up by reflection, and JSON keys come
#     from the serial names compiled into the serializer, so R8 renaming the fields is safe.
#
# GameStatus is stored in Room by Enum.name and read back with valueOf. The name string is a
# constructor argument, not the field's identifier, so renaming does not change it, and
# proguard-android-optimize.txt already keeps values()/valueOf() for enums. No rule added.

# Keep file and line information so crash stack traces from Play Console can be retraced with
# the mapping.txt that the App Bundle carries. The real source file name is hidden.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
