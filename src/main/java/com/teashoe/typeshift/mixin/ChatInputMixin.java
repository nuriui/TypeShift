package com.teashoe.typeshift.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.teashoe.typeshift.client.TypeshiftClient;
import com.teashoe.typeshift.core.InputController;
import com.teashoe.typeshift.core.ReplacementManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(EditBox.class)
public abstract class ChatInputMixin {
    @Shadow private int highlightPos;
    @Shadow private int maxLength;
    @Unique private InputController typeshift$controller;
    @Unique private ReplacementManager.Snapshot typeshift$before;
    @Unique private boolean typeshift$typing;
    @Unique private int typeshift$deletionDepth;
    @Unique private ReplacementManager.Snapshot typeshift$beforeDeletion;

    @Unique private EditBox typeshift$box() { return (EditBox) (Object) this; }
    @Unique private InputController typeshift$active() {
        var screen = Minecraft.getInstance().gui.screen();
        boolean chat = screen instanceof ChatScreen && ((ChatScreenAccessor) screen).typeshift$input() == (Object) this;
        boolean search = screen instanceof CreativeModeInventoryScreen
            && ((CreativeSearchAccessor) screen).typeshift$searchBox() == (Object) this
            && typeshift$box().isVisible() && typeshift$box().isFocused();
        if (!chat && !search) return null;
        if (typeshift$controller == null) typeshift$controller = TypeshiftClient.newController(search);
        return typeshift$controller;
    }
    @Unique private ReplacementManager.Snapshot typeshift$snapshot() {
        return new ReplacementManager.Snapshot(typeshift$box().getValue(), typeshift$box().getCursorPosition(), highlightPos);
    }
    @Unique private void typeshift$apply(ReplacementManager.Edit edit) {
        EditBox box = typeshift$box();
        if (edit.result().text().length() > maxLength) {
            typeshift$controller.external(InputController.Source.EXTERNAL);
            return;
        }
        typeshift$controller.replacements().guarded(() -> {
            box.setCursorPosition(edit.start());
            box.setHighlightPos(edit.end());
            box.insertText(edit.replacement());
            box.setCursorPosition(edit.result().cursor());
            box.setHighlightPos(edit.result().anchor());
        });
    }
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void typeshift$beforeCharacter(CharacterEvent event, CallbackInfoReturnable<Boolean> ci) {
        var controller = typeshift$active();
        if (controller == null || controller.replacements().modifying()) return;
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null && Minecraft.getInstance().gui.screen() instanceof ChatScreen)
            controller.setPlayers(connection.getOnlinePlayers().stream().map(p -> p.getProfile().name()).toList());
        if (controller.wouldExceedRawLimit(typeshift$snapshot(), event.codepoint(), maxLength)) {
            ci.setReturnValue(true);
            return;
        }
        typeshift$before = typeshift$snapshot();
        typeshift$typing = true;
    }
    @Inject(method = "charTyped", at = @At("RETURN"))
    private void typeshift$afterCharacter(CharacterEvent event, CallbackInfoReturnable<Boolean> ci) {
        if (!typeshift$typing) return;
        typeshift$typing = false;
        typeshift$controller.typed(typeshift$before, typeshift$snapshot(), event.codepoint()).ifPresent(this::typeshift$apply);
        typeshift$before = null;
    }
    @Inject(method = {"insertText", "setValue"}, at = @At("HEAD"))
    private void typeshift$externalText(String text, CallbackInfo ci) {
        if (text.isEmpty() && typeshift$box().getValue().isEmpty()) return;
        var controller = typeshift$active();
        if (controller != null && !typeshift$typing && typeshift$deletionDepth == 0 && !controller.replacements().modifying()) controller.external(InputController.Source.EXTERNAL);
    }
    @Inject(method = "preeditUpdated", at = @At("HEAD"))
    private void typeshift$preedit(PreeditEvent event, CallbackInfoReturnable<Boolean> ci) {
        var controller = typeshift$active();
        if (controller != null) controller.preedit(event != null && !event.fullText().isEmpty());
    }
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void typeshift$key(KeyEvent event, CallbackInfoReturnable<Boolean> ci) {
        var controller = typeshift$active();
        if (controller == null || controller.composition().composing()) return;
        if (event.isConfirmation()) {
            var edit = controller.finish(typeshift$snapshot());
            edit.ifPresent(this::typeshift$apply);
            if (edit.isPresent() && Minecraft.getInstance().gui.screen() instanceof CreativeModeInventoryScreen)
                ci.setReturnValue(true);
        } else if (event.hasControlDownWithQuirk() && event.shortcutKey() == InputConstants.KEYCODE_Z && !event.hasShiftDown() && !event.hasAltDown()) {
            var edit = controller.undo(typeshift$snapshot());
            if (edit.isPresent() && edit.get().result().text().length() <= maxLength) {
                typeshift$apply(edit.get()); ci.setReturnValue(true);
            }
        } else if (event.shortcutKey() == InputConstants.KEYCODE_BACKSPACE && !event.hasControlDownWithQuirk() && !event.hasAltDown()) {
            var edit = controller.backspace(typeshift$snapshot());
            if (edit.isPresent()) { typeshift$apply(edit.get()); ci.setReturnValue(true); }

        } else if (event.isPaste() || event.isCut()) controller.external(InputController.Source.PASTE);
        else if (event.isCycleFocus()) controller.external(InputController.Source.COMPLETION);
        else if (event.isUp() || event.isDown()) controller.external(InputController.Source.HISTORY);
        else if (event.isLeft() || event.isRight() || event.isSelectAll() || event.shortcutKey() == InputConstants.KEYCODE_HOME
            || event.shortcutKey() == InputConstants.KEYCODE_END || event.shortcutKey() == InputConstants.KEYCODE_DELETE)
            controller.external(InputController.Source.NAVIGATION);
    }
    @Inject(method = {"deleteCharsToPos", "deleteWords"}, at = @At("HEAD"))
    private void typeshift$beforeDelete(int position, CallbackInfo ci) {
        var controller = typeshift$active();
        if (controller == null || controller.replacements().modifying()) return;
        if (typeshift$deletionDepth++ == 0) typeshift$beforeDeletion = typeshift$snapshot();
    }
    @Inject(method = {"deleteCharsToPos", "deleteWords"}, at = @At("RETURN"))
    private void typeshift$afterDelete(int position, CallbackInfo ci) {
        if (typeshift$deletionDepth == 0) return;
        if (--typeshift$deletionDepth == 0) {
            typeshift$controller.deleted(typeshift$beforeDeletion, typeshift$snapshot());
            typeshift$beforeDeletion = null;
        }
    }
    @Inject(method = "onClick", at = @At("HEAD"))
    private void typeshift$click(MouseButtonEvent event, boolean doubleClick, CallbackInfo ci) {
        var controller = typeshift$active();
        if (controller != null) controller.external(InputController.Source.MOUSE);
    }
    @Inject(method = "onDrag", at = @At("HEAD"))
    private void typeshift$drag(MouseButtonEvent event, double x, double y, CallbackInfo ci) {
        var controller = typeshift$active();
        if (controller != null) controller.external(InputController.Source.MOUSE);
    }
}
