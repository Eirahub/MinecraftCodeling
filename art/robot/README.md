# Robot model sources

Editable sources for the Codeling robot (issue #242). The game does not read anything in this folder. It ships the finished files under `common/src/main/resources/assets/codeling/`, and nothing under `art/` goes into a mod jar. The model, textures and masks are original work for this project and fall under the repository's MIT licence ([LICENSE](../../LICENSE)).

## Files

| File | What it is |
|---|---|
| `codeling_robot_workshop.bbmodel`, `codeling_robot_industrial.bbmodel`, `codeling_robot_mint.bbmodel` | Blockbench projects. Same geometry and animations, one per style. Each embeds all three PNGs and shows its own style. |
| `robot_workshop.png`, `robot_industrial.png`, `robot_mint.png` | The three 64 x 64 style textures (UV atlases, not player skins). |
| `robot_primary_mask.png`, `robot_accent_mask.png`, `robot_lamp_mask.png` | White-on-transparent masks on the same atlas, for the panel, accent and lamp areas. |
| `robot_model.json` | Geometry, per-face UVs, style list and animations in the format the game's renderer reads. |
| `robot_animations.json` | The animation tracks alone (idle, walk, turns, look clips, wave). |
| `robot_uv_layout.json` | The UV rectangle of every cube face. |
| `build_assets.py` | Generates all of the above from one script. |

The model has seven bones (`root`, `body`, `head`, `left_arm`, `right_arm`, `left_leg`, `right_leg`) and eight cuboids, 16 units per block, Y up, facing negative Z.

## Edit in Blockbench

Open one `.bbmodel` in Blockbench 5 or newer (the Generic Model format, no plugin). Use Edit mode for geometry, Paint mode for texels and Animate mode for the clips. Selecting another texture in the texture panel does not remap the faces, so open the project of the style you want to edit.

## Get a change into the game

1. Export or copy the result so you have the texture PNGs and the model JSON.
2. Replace the matching files in `common/src/main/resources/assets/codeling/`: `models/entity/robot.json` and `textures/entity/robot_<style>.png`.
3. Test in the game.

`build_assets.py` can write the model JSON and the PNGs from code. It needs Python 3 with Pillow and writes next to itself. It overwrites the generated textures, masks, JSON files and `.bbmodel` files in this folder, so keep hand edits made in Blockbench somewhere else before you run it. It also writes `asset-validation.json` and `texture-atlases-preview.png`, which are not kept here. The script was not run when these files were added.

## State when added

- The three style PNGs and `robot_primary_mask.png` are byte-identical to the shipped textures.
- `robot_accent_mask.png` and `robot_lamp_mask.png` are not shipped in the game.
- `robot_model.json` is identical to the shipped `robot.json` as stored in git (same blob). The kit's original copy had Windows line endings; the repository converts them to LF.
