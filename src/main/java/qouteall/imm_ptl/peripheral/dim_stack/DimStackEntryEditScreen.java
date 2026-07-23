// 本文件编辑单个维度堆叠条目的隔离草稿。
package qouteall.imm_ptl.peripheral.dim_stack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.GuiHelper;

import java.util.OptionalInt;
import java.util.function.Consumer;

public class DimStackEntryEditScreen extends Screen {
    
    
    private final DimStackScreen parent;
    private final DimEntryWidget editing;
    private final DimStackEntry draft;
    
    private final EditBox scaleField;
    private final Button flipButton;
    private final EditBox horizontalRotationField;
    private final EditBox topYField;
    private final EditBox bottomYField;
    private final EditBox bedrockBlockField;
    private final Button connectsPreviousButton;
    private final Button connectsNextButton;
    
    private final Button finishButton;
    
    private final GuiHelper.Rect scaleLabelRect = new GuiHelper.Rect();
    private final GuiHelper.Rect flipLabelRect = new GuiHelper.Rect();
    private final GuiHelper.Rect horizontalRotationLabelRect = new GuiHelper.Rect();
    private final GuiHelper.Rect topYLabelRect = new GuiHelper.Rect();
    private final GuiHelper.Rect bottomYLabelRect = new GuiHelper.Rect();
    private final GuiHelper.Rect bedrockLabelRect = new GuiHelper.Rect();
    private final GuiHelper.Rect connectsPreviousRect = new GuiHelper.Rect();
    private final GuiHelper.Rect connectsNextRect = new GuiHelper.Rect();
    
    private final Button helpButton;
    
    protected DimStackEntryEditScreen(
        DimStackScreen parent,
        DimEntryWidget editing,
        Consumer<DimStackEntry> callback
    ) {
        super(Component.literal("you cannot see me"));
        
        this.parent = parent;
        this.editing = editing;
        this.draft = editing.entry.copy();
        
        scaleField = new EditBox(
            Minecraft.getInstance().font,
            0, 0, 0, 20, Component.literal("you cannot see me")
        );
        scaleField.setValue(Double.toString(draft.scale));
        scaleField.setHighlightPos(0);//without this the text won't render. mc gui is bugged
        scaleField.setCursorPosition(0);
        
        flipButton =
            Button.builder(
                    Component.translatable(draft.flipped ? "imm_ptl.enabled" : "imm_ptl.disabled"),
                    button -> {
                        draft.flipped = !draft.flipped;
                        button.setMessage(
                            Component.translatable(draft.flipped ? "imm_ptl.enabled" : "imm_ptl.disabled")
                        );
                    }
                )
                .build();
        
        horizontalRotationField = new EditBox(
            Minecraft.getInstance().font,
            0, 0, 0, 20,
            Component.literal("you cannot see me")
        );
        horizontalRotationField.setValue(Double.toString(draft.horizontalRotation));
        horizontalRotationField.setCursorPosition(0);
        horizontalRotationField.setHighlightPos(0);
        
        topYField = new EditBox(
            Minecraft.getInstance().font,
            0, 0, 0, 20,
            Component.literal("you cannot see me")
        );
        if (draft.topY != null) {
            topYField.setValue(Integer.toString(draft.topY));
        }
        topYField.setCursorPosition(0);
        topYField.setHighlightPos(0);
        
        bottomYField = new EditBox(
            Minecraft.getInstance().font,
            0, 0, 0, 20,
            Component.literal("you cannot see me")
        );
        if (draft.bottomY != null) {
            bottomYField.setValue(Integer.toString(draft.bottomY));
        }
        bottomYField.setCursorPosition(0);
        bottomYField.setHighlightPos(0);
        
        bedrockBlockField = new EditBox(
            Minecraft.getInstance().font,
            0, 0, 0, 20,
            Component.literal("you cannot see me")
        );
        bedrockBlockField.setMaxLength(200);
        if (draft.bedrockReplacementStr != null) {
            bedrockBlockField.setValue(draft.bedrockReplacementStr);
        }
        bedrockBlockField.setCursorPosition(0);
        bedrockBlockField.setHighlightPos(0);
        
        connectsPreviousButton = Button.builder(
            Component.translatable(draft.connectsPrevious ? "imm_ptl.enabled" : "imm_ptl.disabled"),
            button -> {
                draft.connectsPrevious = !draft.connectsPrevious;
                button.setMessage(
                    Component.translatable(draft.connectsPrevious ? "imm_ptl.enabled" : "imm_ptl.disabled")
                );
            }
        ).build();
        
        connectsNextButton = Button.builder(
            Component.translatable(draft.connectsNext ? "imm_ptl.enabled" : "imm_ptl.disabled"),
            button -> {
                draft.connectsNext = !draft.connectsNext;
                button.setMessage(
                    Component.translatable(draft.connectsNext ? "imm_ptl.enabled" : "imm_ptl.disabled")
                );
            }
        ).build();
        
        finishButton = Button.builder(
            Component.translatable("imm_ptl.finish"),
            button -> {
                draft.horizontalRotation =
                    Helper.parseDouble(horizontalRotationField.getValue()).orElse(0);
                
                draft.scale =
                    Helper.parseDouble(scaleField.getValue()).orElse(1);
                
                OptionalInt topY = Helper.parseInt(topYField.getValue());
                draft.topY = topY.isPresent() ? topY.getAsInt() : null;
                
                OptionalInt bottomY = Helper.parseInt(bottomYField.getValue());
                draft.bottomY = bottomY.isPresent() ? bottomY.getAsInt() : null;
                
                draft.bedrockReplacementStr = bedrockBlockField.getValue();
                
                Minecraft.getInstance().setScreen(parent);
                callback.accept(draft);
            }
        ).build();
        
        this.helpButton = DimStackScreen.createHelpButton(this);
    }
    
    
    @Override
    public void tick() {
        super.tick();
    }
    
    @Override
    protected void init() {
        addWidget(scaleField);
        addRenderableWidget(flipButton);
        addWidget(horizontalRotationField);
        addWidget(topYField);
        addWidget(bottomYField);
        addWidget(bedrockBlockField);
        addWidget(connectsPreviousButton);
        addWidget(connectsNextButton);
        addRenderableWidget(finishButton);
        addRenderableWidget(helpButton);
        
        GuiHelper.layout(
            0, height,
            GuiHelper.blankSpace(5),
            GuiHelper.fixedLength(20,
                GuiHelper.combine(
                    GuiHelper.layoutRectVertically(scaleLabelRect),
                    GuiHelper.layoutButtonVertically(scaleField)
                )
            ),
            GuiHelper.elasticBlankSpace(),
            GuiHelper.fixedLength(20,
                GuiHelper.combine(
                    GuiHelper.layoutRectVertically(flipLabelRect),
                    GuiHelper.layoutButtonVertically(flipButton)
                )
            ),
            GuiHelper.elasticBlankSpace(),
            GuiHelper.fixedLength(20,
                GuiHelper.combine(
                    GuiHelper.layoutRectVertically(horizontalRotationLabelRect),
                    GuiHelper.layoutButtonVertically(horizontalRotationField)
                )
            ),
            GuiHelper.elasticBlankSpace(),
            GuiHelper.fixedLength(20,
                GuiHelper.combine(
                    GuiHelper.layoutRectVertically(topYLabelRect),
                    GuiHelper.layoutButtonVertically(topYField)
                )
            ),
            GuiHelper.elasticBlankSpace(),
            GuiHelper.fixedLength(20,
                GuiHelper.combine(
                    GuiHelper.layoutRectVertically(bottomYLabelRect),
                    GuiHelper.layoutButtonVertically(bottomYField)
                )
            ),
            GuiHelper.elasticBlankSpace(),
            GuiHelper.fixedLength(20,
                GuiHelper.combine(
                    GuiHelper.layoutRectVertically(bedrockLabelRect),
                    GuiHelper.layoutButtonVertically(bedrockBlockField)
                )
            ),
            GuiHelper.elasticBlankSpace(),
            GuiHelper.fixedLength(20,
                GuiHelper.combine(
                    GuiHelper.layoutRectVertically(connectsPreviousRect),
                    GuiHelper.layoutButtonVertically(connectsPreviousButton)
                )
            ),
            GuiHelper.elasticBlankSpace(),
            GuiHelper.fixedLength(20,
                GuiHelper.combine(
                    GuiHelper.layoutRectVertically(connectsNextRect),
                    GuiHelper.layoutButtonVertically(connectsNextButton)
                )
            ),
            GuiHelper.elasticBlankSpace(),
            GuiHelper.fixedLength(20, GuiHelper.layoutButtonVertically(finishButton)),
            GuiHelper.blankSpace(5)
        );
        
        GuiHelper.layout(
            0, width,
            GuiHelper.elasticBlankSpace(),
            GuiHelper.fixedLength(150,
                GuiHelper.combine(
                    GuiHelper.layoutRectHorizontally(scaleLabelRect),
                    GuiHelper.layoutRectHorizontally(flipLabelRect),
                    GuiHelper.layoutRectHorizontally(horizontalRotationLabelRect),
                    GuiHelper.layoutRectHorizontally(topYLabelRect),
                    GuiHelper.layoutRectHorizontally(bottomYLabelRect),
                    GuiHelper.layoutRectHorizontally(bedrockLabelRect),
                    GuiHelper.layoutRectHorizontally(connectsPreviousRect),
                    GuiHelper.layoutRectHorizontally(connectsNextRect)
                )
            ),
            GuiHelper.blankSpace(20),
            GuiHelper.fixedLength(100,
                GuiHelper.combine(
                    GuiHelper.layoutButtonHorizontally(scaleField),
                    GuiHelper.layoutButtonHorizontally(flipButton),
                    GuiHelper.layoutButtonHorizontally(horizontalRotationField),
                    GuiHelper.layoutButtonHorizontally(topYField),
                    GuiHelper.layoutButtonHorizontally(bottomYField),
                    GuiHelper.layoutButtonHorizontally(bedrockBlockField),
                    GuiHelper.layoutButtonHorizontally(connectsPreviousButton),
                    GuiHelper.layoutButtonHorizontally(connectsNextButton)
                )
            ),
            GuiHelper.elasticBlankSpace()
        );
        
        GuiHelper.layout(
            0, width,
            GuiHelper.blankSpace(20),
            new GuiHelper.LayoutElement(
                true, 100,
                GuiHelper.layoutButtonHorizontally(finishButton)
            ),
            GuiHelper.elasticBlankSpace()
        );
        
        helpButton.setX(width - 50);
        helpButton.setY(5);
        helpButton.setWidth(20);
    }
    
    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(guiGraphics, mouseX, mouseY, delta);
        
        super.render(guiGraphics, mouseX, mouseY, delta);
        
        scaleField.render(guiGraphics, mouseX, mouseY, delta);
        horizontalRotationField.render(guiGraphics, mouseX, mouseY, delta);
        topYField.render(guiGraphics, mouseX, mouseY, delta);
        bottomYField.render(guiGraphics, mouseX, mouseY, delta);
        bedrockBlockField.render(guiGraphics, mouseX, mouseY, delta);
        connectsPreviousButton.render(guiGraphics, mouseX, mouseY, delta);
        connectsNextButton.render(guiGraphics, mouseX, mouseY, delta);
        
        scaleLabelRect.renderTextLeft(Component.translatable("imm_ptl.scale"), guiGraphics);
        flipLabelRect.renderTextLeft(Component.translatable("imm_ptl.flipped"), guiGraphics);
        horizontalRotationLabelRect.renderTextLeft(Component.translatable("imm_ptl.horizontal_rotation"), guiGraphics);
        topYLabelRect.renderTextLeft(Component.translatable("imm_ptl.top_y"), guiGraphics);
        bottomYLabelRect.renderTextLeft(Component.translatable("imm_ptl.bottom_y"), guiGraphics);
        bedrockLabelRect.renderTextLeft(Component.translatable("imm_ptl.bedrock_replacement"), guiGraphics);
        connectsPreviousRect.renderTextLeft(Component.translatable("imm_ptl.connects_previous"), guiGraphics);
        connectsNextRect.renderTextLeft(Component.translatable("imm_ptl.connects_next"), guiGraphics);
    }
}
