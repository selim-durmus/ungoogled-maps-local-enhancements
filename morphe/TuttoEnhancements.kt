package tutto.patches

import app.morphe.patcher.patch.*
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val UI = "Lorg/ungoogled/ui/"
private const val PLACE = "${UI}SavedStore\$Place;"
private const val ACTIVITY = "Landroid/app/Activity;"
private const val CONTEXT = "Landroid/content/Context;"
private const val STRING = "Ljava/lang/String;"
private const val OBJECT = "Ljava/lang/Object;"

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
    field(store, "home", PLACE); field(store, "work", PLACE); field(store, "labels", "Ljava/util/Map;")
    field(PLACE, "lat", "D"); field(PLACE, "lng", "D"); field(PLACE, "name", STRING)
    field(PLACE, "ftid", STRING); field(PLACE, "lists", "Ljava/util/Set;")
    method(store, "labelsFor", "Ljava/util/List;", PLACE)
    method(store, "setLabel", "V", CONTEXT, STRING, PLACE)
    method(store, "removeLabel", "V", CONTEXT, STRING)
    method(store, "setHome", "V", CONTEXT, PLACE); method(store, "setWork", "V", CONTEXT, PLACE)
    method(store, "aliasOf", PLACE, PLACE); method(store, "load", "V", CONTEXT)
    method(store, "allSaved", "Ljava/util/List;")
    method("${UI}SavedPlaces;", "directions", "V", CONTEXT, PLACE)
    method("${UI}SavedPlaces;", "open", "V", CONTEXT, PLACE)
    method("${UI}SavedPlaces;", "dialogTheme", "I", CONTEXT)
    method("${UI}SavedPlaces;", "place", PLACE, STRING, OBJECT, OBJECT)
    field("Latqs;", "a", "Lnxb;"); field("Latqs;", "l", "Lawvj;")
    field("Latlg;", "a", OBJECT); field("Latqq;", "a", "Latqs;"); field("Lareb;", "c", OBJECT)
    method("Lawvj;", "a", "Ljava/io/Serializable;")
    method("Loku;", "bz", STRING); method("Loku;", "p", "Lbjap;"); method("Loku;", "q", "Lbjaw;")
}

@Suppress("unused")
val tuttoEnhancementsPatch = bytecodePatch(
    name = "Home, Work, markers and labels",
    description = "Adds Home/Work shortcuts with clean Back navigation, dark map markers and searchable " +
        "local labels. Select alongside bearinmind's Offline saved places. Tested with bearinmind " +
        "1.7.4 and Maps 26.36.04.973607363. Does not support Add microG support.",
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
                invoke-static {p1}, ${UI}LocalMarkers;->$action($ACTIVITY)V
            """)
        }
        val native = method("Latqq;", "a", "V", "Lbcio;")
        requireCompatible(native.implementation?.registerCount == 10, "Native label registers changed")
        native.addInstructionsWithLabels(0, """
            iget-object v0, p0, Latqq;->a:Latqs;
            invoke-static {v0}, ${UI}LocalLabels;->editNative($OBJECT)Z
            move-result v0
            if-eqz v0, :original
            return-void
        """, ExternalLabel("original", native.implementation!!.instructions.first()))

        val chip = method("Lareb;", "onClick", "V", "Landroid/view/View;")
        requireCompatible(chip.implementation?.registerCount == 11, "Place-sheet label registers changed")
        val ins = chip.implementation!!.instructions
        val casts = ins.indices.filter { ins[it].opcode == Opcode.CHECK_CAST &&
            ((ins[it] as? ReferenceInstruction)?.reference as? TypeReference)?.type == "Latlg;" }
        requireCompatible(casts.size == 1, "Place-sheet label anchor changed")
        val next = casts.single() + 1
        chip.addInstructionsWithLabels(next, """
            iget-object v0, v1, Latlg;->a:$OBJECT
            iget-object v2, p0, Lareb;->c:$OBJECT
            invoke-static {v0, v2}, ${UI}LocalLabels;->editNativePlace($OBJECT$OBJECT)Z
            move-result v0
            if-eqz v0, :original
            return-void
        """, ExternalLabel("original", ins[next]))

        val owner = mutableClassDefBy("${UI}YouActivity;")
        val old = method(owner.type, "labelDialog", "V", PLACE, STRING)
        requireCompatible(AccessFlags.PRIVATE.isSet(old.accessFlags), "Local label dialog visibility changed")
        val replacement = ImmutableMethod(old.definingClass, old.name, old.parameters, old.returnType,
            old.accessFlags, old.annotations, old.hiddenApiRestrictions, MutableMethodImplementation(3)).toMutable()
        replacement.addInstructions(0, """
            invoke-static {p0, p1, p2}, ${UI}LocalLabels;->edit($ACTIVITY$PLACE$STRING)V
            return-void
        """)
        owner.methods.remove(old)
        owner.methods.add(replacement)
    }
}
