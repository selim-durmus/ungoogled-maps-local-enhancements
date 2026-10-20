package tutto.patches

import app.morphe.patcher.patch.*
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val UI = "Lorg/ungoogled/ui/"
private const val PLACE = "${UI}SavedStore\$Place;"
private const val ACTIVITY = "Landroid/app/Activity;"
private const val CONTEXT = "Landroid/content/Context;"

private fun requireCompatible(ok: Boolean, reason: String) {
    if (!ok) throw PatchException("Tutto Enhancements: $reason. Use the supported Maps version with " +
        "bearinmind patches and Offline saved places enabled; do not enable Add microG support.")
}

private fun BytecodePatchContext.method(owner: String, name: String, result: String, vararg params: String): MutableMethod {
    val matches = mutableClassDefByOrNull(owner)?.methods?.filter {
        it.name == name && it.returnType == result && it.parameterTypes.map(CharSequence::toString) == params.toList()
    }.orEmpty()
    requireCompatible(matches.size == 1, "Required method changed: $owner->$name")
    return matches.single()
}

private fun BytecodePatchContext.field(owner: String, name: String, type: String) {
    requireCompatible(classDefByOrNull(owner)?.fields?.any { it.name == name && it.type == type } == true,
        "Required field changed: $owner->$name")
}

private fun Method.calls(owner: String, name: String? = null) = implementation?.instructions?.any {
    val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
    ref?.definingClass == owner && (name == null || ref.name == name)
} == true

private fun BytecodePatchContext.checkBindings() {
    requireCompatible(classDefByOrNull("${UI}SavedStore;") != null, "Local saved extension is missing")
    val microg = classDefByOrNull("${UI}Shapes;")?.methods?.singleOrNull { it.name == "microgPatched" }
    if (microg != null) requireCompatible(
        (microg.implementation?.instructions?.firstOrNull() as? NarrowLiteralInstruction)?.narrowLiteral == 0,
        "The microG variant is not supported")

    // Merely finding SavedStore is insufficient: upstream includes it even when saving is disabled.
    var savingEnabled = false
    classDefForEach { c ->
        if (!savingEnabled && !c.type.startsWith(UI))
            savingEnabled = c.methods.any { it.calls("${UI}SavedPlaces;", "save") }
    }
    requireCompatible(savingEnabled, "Offline saved places was not applied")
    val store = "${UI}SavedStore;"
    field(store, "home", PLACE); field(store, "work", PLACE)
    field(PLACE, "lat", "D"); field(PLACE, "lng", "D")
    method(store, "load", "V", CONTEXT)
    method("${UI}SavedPlaces;", "directions", "V", CONTEXT, PLACE)
}

@Suppress("unused")
val tuttoEnhancementsPatch = bytecodePatch(
    name = "Home and Work shortcuts",
    description = "Adds Home and Work shortcut chips beside Maps' category carousel, with clean Back " +
        "navigation. Select alongside bearinmind's Offline saved places, which draws saved places, " +
        "labels and starred places on the map. Built for bearinmind 2.5.5 and Maps " +
        "26.36.04.973607363. Does not support Add microG support.",
    default = true,
) {
    compatibleWith(Compatibility(name = "Google Maps", packageName = "com.google.android.apps.maps",
        apkFileType = ApkFileType.APKM, appIconColor = 0xFFFFFF,
        targets = listOf(AppTarget(version = "26.36.04.973607363"))))
    extendWith("extensions/tutto.mpe")
    // A cross-source dependsOn would bundle/execute another copy of upstream patches.
    // Finalize runs after ALL execute blocks; validate upstream ABI before touching its methods.
    finalize {
        checkBindings()
        for ((event, action) in listOf("Resumed" to "resume", "Paused" to "pause", "Destroyed" to "pause")) {
            val m = method("${UI}SavedPlaces\$Front;", "onActivity$event", "V", ACTIVITY)
            requireCompatible(!m.calls("${UI}HomeWorkShortcuts;") && !m.calls("${UI}LocalMarkers;"),
                "The input already contains our enhancements; start from the original APK")
            m.addInstructions(0, """
                invoke-static {p1}, ${UI}HomeWorkShortcuts;->$action($ACTIVITY)V
            """)
        }
    }
}
