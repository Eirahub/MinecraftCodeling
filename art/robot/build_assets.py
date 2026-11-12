"""Author exact Minecraft robot pixels, UVs and Blockbench animation projects.

These are new native UV assets, not resized/generated concept renders.
Requires Pillow; all output is relative to this script.
"""
from __future__ import annotations

import base64
import copy
import hashlib
import json
import math
from pathlib import Path
import uuid

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
SIZE = 64
FACES = ("north", "east", "south", "west", "up", "down")
NS = uuid.UUID("a0f58fda-56a3-4465-b058-dd870fbce9fd")


def uid(name):
    return str(uuid.uuid5(NS, name))


def cube(name, origin, size):
    return {"name": name, "from": origin,
            "to": [a+b for a, b in zip(origin, size)], "faces": {}}


BONES = [
    {"name": "root", "parent": None, "pivot": [0, 0, 0], "cubes": []},
    {"name": "body", "parent": "root", "pivot": [0, 3, 0], "cubes": [
        cube("torso", [-3, 4, -2], [6, 4, 4]),
        cube("waist", [-2, 3, -1.5], [4, 1, 3]),
        cube("neck", [-1, 8, -1], [2, 1, 2])]},
    {"name": "head", "parent": "body", "pivot": [0, 9, 0], "cubes": [
        cube("head", [-4, 9, -3], [8, 7, 6])]},
    {"name": "left_arm", "parent": "body", "pivot": [4.5, 8, 0], "cubes": [
        cube("left_arm", [3.5, 3, -1], [2, 6, 2])]},
    {"name": "right_arm", "parent": "body", "pivot": [-4.5, 8, 0], "cubes": [
        cube("right_arm", [-5.5, 3, -1], [2, 6, 2])]},
    {"name": "left_leg", "parent": "body", "pivot": [1.5, 3, 0], "cubes": [
        cube("left_foot", [0.5, 0, -2], [2, 3, 3])]},
    {"name": "right_leg", "parent": "body", "pivot": [-1.5, 3, 0], "cubes": [
        cube("right_foot", [-2.5, 0, -2], [2, 3, 3])]},
]


def make_uv():
    rectangles = []
    for bone in BONES:
        for c in bone["cubes"]:
            x, y, z = [int(b-a) for a, b in zip(c["from"], c["to"])]
            for face in FACES:
                w, h = (x, y) if face in ("north", "south") else ((z, y) if face in ("east", "west") else (x, z))
                rectangles.append((h, w, c["name"], face, c))
    rectangles.sort(key=lambda item: (-item[0], -item[1], item[2], item[3]))
    x = y = 1
    row_height = 0
    for h, w, _, face, c in rectangles:
        if x+w > SIZE-1:
            x, y = 1, y+row_height+1
            row_height = 0
        assert y+h < SIZE, "UV atlas overflow"
        c["faces"][face] = {"uv": [x, y, x+w, y+h], "rotation": 0}
        x += w+1
        row_height = max(row_height, h)


PALETTES = {
    "workshop": {"metal": "#B8B9BA", "face": "#ECE7D9", "joint": "#373B3E", "primary": "#18A6B4", "accent": "#57676C", "lamp": "#FFBD38", "eye": "#20272B"},
    "industrial": {"metal": "#41484C", "face": "#C9D0D0", "joint": "#272E32", "primary": "#E2A51E", "accent": "#A9B2B4", "lamp": "#FFF0C9", "eye": "#182024"},
    "mint": {"metal": "#E6DFCF", "face": "#AED0B3", "joint": "#5D6564", "primary": "#A4CBB0", "accent": "#E6816A", "lamp": "#FFE9AF", "eye": "#273737"},
}


def rgba(hex_color):
    return tuple(int(hex_color[i:i+2], 16) for i in (1, 3, 5)) + (255,)


def shade(color, amount):
    return tuple(max(0, min(255, c+amount)) for c in color[:3])+(255,)


def write_textures():
    mask_regions = {name: Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0)) for name in ("primary", "accent", "lamp")}
    for variant, palette_hex in PALETTES.items():
        p = {k: rgba(v) for k, v in palette_hex.items()}
        atlas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        for bone in BONES:
            for c in bone["cubes"]:
                name = c["name"]
                for face, definition in c["faces"].items():
                    x0, y0, x1, y1 = definition["uv"]
                    w, h = x1-x0, y1-y0
                    patch = Image.new("RGBA", (w, h), p["metal"])
                    pixels = patch.load()
                    local_masks = {n: Image.new("RGBA", (w, h), (0, 0, 0, 0)) for n in mask_regions}
                    def put(x, y, material, adjustment=0):
                        if 0 <= x < w and 0 <= y < h:
                            pixels[x, y] = shade(p[material], adjustment)
                            if material in local_masks:
                                local_masks[material].putpixel((x, y), (255, 255, 255, 255))
                    for y in range(h):
                        for x in range(w):
                            noise = ((x*7+y*11+sum(map(ord, name+face))) % 13)
                            amount = 5 if noise == 0 else (-5 if noise == 1 else 0)
                            pixels[x, y] = shade(p["metal"], amount)
                    if name in ("neck", "waist"):
                        patch.paste(p["joint"], (0, 0, w, h))
                    elif name == "head":
                        if face == "north":
                            for y in range(1, h-1):
                                for x in range(1, w-1):
                                    put(x, y, "face", (2 if (x+y)%7 == 0 else 0))
                            # Native face: two distinct dark eyes, not a baked perspective image.
                            for ex in (2, 5):
                                put(ex, 3, "eye")
                                put(ex, 4, "eye")
                            for x, y in ((0, 0), (w-1, 0), (0, h-1), (w-1, h-1)):
                                put(x, y, "joint", 12)
                        elif face in ("east", "west", "south"):
                            for y in range(1, h-1):
                                for x in range(1, w-1):
                                    put(x, y, "primary", (5 if (x+y)%7 == 0 else 0))
                            if face == "south":
                                for x in (w//2-1, w//2):
                                    put(x, h-2, "joint", 8)
                        else:
                            for y in range(1, h-1):
                                for x in range(1, w-1):
                                    put(x, y, "primary", 3 if (x+y)%6 == 0 else 0)
                    elif name == "torso":
                        if face in ("north", "south"):
                            for y in range(h):
                                for x in range(1, w-1):
                                    put(x, y, "primary", 4 if (x+y)%5 == 0 else 0)
                            for x, y in ((0, 0), (w-1, 0), (0, h-1), (w-1, h-1)):
                                put(x, y, "joint", 14)
                            if face == "north":
                                for y in (1, 2):
                                    for x in (2, 3):
                                        put(x, y, "lamp", 10 if (x,y) == (2,1) else -12)
                            else:
                                for x in (2, 3):
                                    put(x, 2, "joint", 8)
                        elif face in ("east", "west"):
                            for y in range(1, h-1):
                                for x in range(1, w-1):
                                    put(x, y, "primary")
                    elif "arm" in name:
                        for x in range(w):
                            put(x, 1, "joint", 7)
                            put(x, h-1, "joint")
                            if variant == "industrial":
                                for y in range(2, h-1):
                                    put(x, y, "primary", 5 if y%3 == 0 else 0)
                            elif variant == "mint":
                                put(x, h-2, "accent")
                            elif face in ("east", "west"):
                                put(x, 3, "primary")
                    elif "foot" in name:
                        if face in ("north", "east", "south", "west"):
                            for x in range(w):
                                put(x, 0, "joint")
                                put(x, h-1, "joint", 8)
                            if face == "north":
                                for x in range(w):
                                    put(x, 1, "primary", 8)
                        elif face == "down":
                            patch.paste(p["joint"], (0, 0, w, h))
                    atlas.paste(patch, (x0, y0))
                    # Tint masks are the union of user-colourable surfaces across variants.
                    for key, local in local_masks.items():
                        mask_regions[key].alpha_composite(local, (x0, y0))
        atlas.save(ROOT/f"robot_{variant}.png", optimize=True)
    for key, image in mask_regions.items():
        image.save(ROOT/f"robot_{key}_mask.png", optimize=True)


def keys(points):
    return [{"time": t, "value": value} for t, value in points]


def sample(points, time):
    if time <= points[0]["time"]:
        return points[0]["value"]
    for a,b in zip(points,points[1:]):
        if time <= b["time"]:
            factor = (time-a["time"])/(b["time"]-a["time"])
            return [x+(y-x)*factor for x,y in zip(a["value"],b["value"])]
    return points[-1]["value"]


def make_animations():
    z = [0, 0, 0]
    animations = {}
    def add(name, length, loop, tracks):
        animations[name] = {"length": length, "loop": loop, "interpolation": "linear", "bones": tracks}
    add("idle", 2.4, True, {
        "head": {"rotation": keys([(0,z),(.6,[0,2,1]),(1.2,z),(1.8,[0,-2,-1]),(2.4,z)])},
        "left_arm": {"rotation": keys([(0,z),(1.2,[2,0,0]),(2.4,z)])},
        "right_arm": {"rotation": keys([(0,z),(1.2,[-2,0,0]),(2.4,z)])}})
    walk = {}
    for bone, sign in (("left_leg",1),("right_leg",-1),("left_arm",-1),("right_arm",1)):
        walk[bone] = {"rotation": keys([(0,[sign*22,0,0]),(.25,z),(.5,[-sign*22,0,0]),(.75,z),(1,[sign*22,0,0])])}
    add("walk", 1, True, walk)
    for name, sign in (("turn_left",1),("turn_right",-1)):
        tracks = {"root": {"rotation": keys([(0,z),(.15,[0,sign*12,0]),(.45,[0,sign*78,0]),(.6,[0,sign*90,0])])}}
        for bone, legsign in (("left_leg",1),("right_leg",-1)):
            tracks[bone] = {"rotation": keys([(0,z),(.15,[legsign*12,0,0]),(.3,z),(.45,[-legsign*12,0,0]),(.6,z)])}
        add(name, .6, False, tracks)
    for name, end in (("look_left",[0,35,0]),("look_right",[0,-35,0]),("look_up",[25,0,0]),("look_down",[-25,0,0])):
        add(name, 1.2, False, {"head": {"rotation": keys([(0,z),(.3,end),(.9,end),(1.2,z)])}})
    add("wave", 1.6, False, {
        "right_arm": {"rotation": keys([(0,z),(.35,[0,0,-130]),(.6,[0,0,-155]),(.85,[0,0,-120]),(1.1,[0,0,-155]),(1.3,[0,0,-130]),(1.6,z)])},
        "head": {"rotation": keys([(0,z),(.35,[0,-8,-5]),(1.3,[0,-8,-5]),(1.6,z)])}})
    # Feet rotate around their ankle. Lift their long toes enough to clear the floor.
    for animation in animations.values():
        for bone in ("left_leg", "right_leg"):
            track = animation["bones"].get(bone)
            if not track:
                continue
            points = []
            count = round(animation["length"]*80)
            for index in range(count+1):
                time = round(animation["length"]*index/count, 6)
                angle = math.radians(sample(track["rotation"],time)[0])
                correction = max(0, 3*math.cos(angle)+(math.sin(angle) if angle >= 0 else -2*math.sin(angle))-3)
                # A small margin covers the chord error between sampled keyframes.
                if abs(angle) > 1e-8:
                    correction += .002
                points.append((time,[0,round(correction,6),0]))
            track["position"] = keys(points)
    return animations


def bbmodel(model, variant_index):
    elements = []
    for bone in BONES:
        for c in bone["cubes"]:
            element = {"name": c["name"], "type": "cube", "uuid": uid("cube/"+c["name"]),
                       "from": c["from"], "to": c["to"], "origin": bone["pivot"], "rotation": [0,0,0],
                       "box_uv": False, "rescale": False, "locked": False, "visibility": True,
                       "autouv": 0, "color": 0, "faces": {}}
            for face, data in c["faces"].items():
                element["faces"][face] = {"uv": data["uv"], "texture": variant_index, "rotation": 0}
            elements.append(element)
    groups = [{"name": bone["name"], "uuid": uid("bone/"+bone["name"]), "origin": bone["pivot"],
               "rotation": [0,0,0], "color": i, "export": True, "isOpen": True,
               "locked": False, "visibility": True, "autouv": 0} for i, bone in enumerate(BONES)]
    def outline(name):
        bone = next(b for b in BONES if b["name"] == name)
        return {"uuid": uid("bone/"+name), "children":
                [uid("cube/"+c["name"]) for c in bone["cubes"]] +
                [outline(child["name"]) for child in BONES if child["parent"] == name]}
    textures = []
    for index, variant in enumerate(PALETTES):
        path = ROOT/f"robot_{variant}.png"
        textures.append({"name": path.name, "id": str(index), "uuid": uid("texture/"+variant),
                         "relative_path": path.name, "source": "data:image/png;base64,"+base64.b64encode(path.read_bytes()).decode(),
                         "mode": "bitmap", "width": SIZE, "height": SIZE, "uv_width": SIZE, "uv_height": SIZE,
                         "internal": True, "saved": True, "render_mode": "default", "render_sides": "auto",
                         "visible": True, "default": index == variant_index})
    anims = []
    for name, animation in model["animations"].items():
        animators = {}
        for bone_name, channels in animation["bones"].items():
            frames = []
            for channel, points in channels.items():
                for point in points:
                    frames.append({"channel": channel, "time": point["time"],
                                   "data_points": [{axis: str(value) for axis, value in zip("xyz", point["value"])}],
                                   "interpolation": "linear", "color": -1,
                                   "uuid": uid(f"kf/{name}/{bone_name}/{channel}/{point['time']}")})
            animators[uid("bone/"+bone_name)] = {"name": bone_name, "type": "bone", "keyframes": frames}
        anims.append({"name": name, "uuid": uid("animation/"+name), "loop": "loop" if animation["loop"] else "once",
                      "override": False, "length": animation["length"], "snapping": 40, "selected": False,
                      "animators": animators})
    return {"meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
            "name": "Codeling Robot", "model_identifier": "codeling_robot", "visible_box": [1,1,0.5],
            "resolution": {"width": SIZE, "height": SIZE}, "elements": elements, "groups": groups,
            "outliner": [outline("root")], "textures": textures, "animations": anims}


def write_json(path, value):
    (ROOT/path).write_text(json.dumps(value, indent=2)+"\n", encoding="utf-8")


def validate(model):
    covered = set()
    cubes = []
    for bone in model["bones"]:
        for c in bone["cubes"]:
            cubes.append(c)
            for axis in range(3):
                assert c["from"][axis] < c["to"][axis]
            for face, data in c["faces"].items():
                x0,y0,x1,y1 = data["uv"]
                assert all(isinstance(v,int) for v in data["uv"])
                assert 0 <= x0 < x1 <= SIZE and 0 <= y0 < y1 <= SIZE
                for y in range(y0,y1):
                    for x in range(x0,x1):
                        assert (x,y) not in covered, f"UV overlap: {c['name']}/{face}"
                        covered.add((x,y))
    bounds = {"from": [min(c["from"][axis] for c in cubes) for axis in range(3)],
              "to": [max(c["to"][axis] for c in cubes) for axis in range(3)]}
    assert all(b-a <= 16 for a,b in zip(bounds["from"],bounds["to"]))
    hashes = []
    for variant in PALETTES:
        path = ROOT/f"robot_{variant}.png"
        with Image.open(path) as image:
            assert image.size == (64,64) and image.mode == "RGBA"
            assert all(image.getpixel(point)[3] == 255 for point in covered)
        hashes.append(hashlib.sha256(path.read_bytes()).hexdigest())
    assert len(set(hashes)) == 3
    names = {b["name"] for b in BONES}
    for name, animation in model["animations"].items():
        for bone, channels in animation["bones"].items():
            assert bone in names
            for channel, points in channels.items():
                assert channel in ("rotation", "position", "scale")
                assert points[0]["time"] == 0
                assert points[-1]["time"] == animation["length"]
                assert all(a["time"] < b["time"] for a,b in zip(points,points[1:]))
                assert all(len(p["value"]) == 3 and all(math.isfinite(v) for v in p["value"]) for p in points)
                if animation["loop"]:
                    assert points[0]["value"] == points[-1]["value"]
    assert model["animations"]["turn_left"]["bones"]["root"]["rotation"][-1]["value"] == [0,90,0]
    assert model["animations"]["turn_right"]["bones"]["root"]["rotation"][-1]["value"] == [0,-90,0]
    for point_a, point_b in zip(model["animations"]["walk"]["bones"]["left_leg"]["rotation"], model["animations"]["walk"]["bones"]["right_leg"]["rotation"]):
        assert point_a["value"][0] == -point_b["value"][0]
    for animation in model["animations"].values():
        for bone in ("left_leg", "right_leg"):
            track = animation["bones"].get(bone)
            if not track:
                continue
            for index in range(801):
                time = animation["length"]*index/800
                angle = math.radians(sample(track["rotation"],time)[0])
                lift = sample(track["position"],time)[1]
                floor = min(3+y*math.cos(angle)-z*math.sin(angle)+lift for y in (-3,0) for z in (-2,1))
                assert floor >= -0.0001, f"Foot penetrates floor by {-floor}: {bone}"
    for variant_index, variant in enumerate(PALETTES):
        project = json.loads((ROOT/f"codeling_robot_{variant}.bbmodel").read_text())
        group_ids = {g["uuid"] for g in project["groups"]}
        element_ids = {e["uuid"] for e in project["elements"]}
        def visit(node):
            assert node["uuid"] in group_ids
            for child in node["children"]:
                if isinstance(child,str):
                    assert child in element_ids
                else:
                    visit(child)
        for node in project["outliner"]: visit(node)
        for anim in project["animations"]:
            assert set(anim["animators"]) <= group_ids
        assert len(project["textures"]) == 3 and len(project["animations"]) == 9
        for element in project["elements"]:
            assert set(element["faces"]) == set(FACES)
            assert all(face["texture"] == variant_index for face in element["faces"].values())
        for texture in project["textures"]:
            assert base64.b64decode(texture["source"].split(",",1)[1]) == (ROOT/texture["name"]).read_bytes()
    report = {"passed": True, "texture_dimensions": [64,64], "variants": list(PALETTES),
              "cubes": len(cubes), "bones": len(BONES), "faces": len(cubes)*6,
              "uv_texels": len(covered), "uv_overlaps": 0, "rest_bounds_model_units": bounds,
              "animation_count": len(model["animations"]), "checks": [
                  "PNG dimensions/mode/opaque mapped texels", "distinct variant SHA256 hashes", "non-overlapping UV islands",
                  "one-block rest bounds", "valid keyframe timing and numeric channels", "seamless looping tracks",
                  "opposed walking legs", "sampled walk/turn feet clear floor", "90 degree turn endpoints", "Blockbench hierarchy and animator references",
                  "Blockbench per-variant face texture references", "embedded PNGs match standalone files"],
              "limits": ["No claim of Java runtime integration or in-game testing", "Blockbench native codec import requires GUI verification"]}
    write_json("asset-validation.json", report)
    print(json.dumps(report, indent=2))


def main():
    make_uv()
    write_textures()
    animations = make_animations()
    model = {"schema": "codeling.robot.asset.v1", "texture_size": [64,64],
             "coordinates": {"units_per_block": 16, "up": "+Y", "front": "-Z", "rotation_unit": "degrees",
                             "rotation_order": "ZYX", "rotation_convention": "right-handed", "bone_pivots": "absolute model space",
                             "rest_bounds": {"from": [-5.5,0,-3], "to": [5.5,16,3]}},
             "variants": [{"id": name, "texture": f"robot_{name}.png", "palette": palette} for name,palette in PALETTES.items()],
             "tint_masks": {k: f"robot_{k}_mask.png" for k in ("primary", "accent", "lamp")},
             "bones": BONES, "animations": animations,
             "animation_notes": {"turns": "Sample root yaw as a delta from the entity's start yaw. Commit the 90-degree entity heading once and clear the turn animation; do not add the final yaw twice.",
                                 "walk": "Loop is in-place. Movement and pathfinding remain the Java entity's responsibility.",
                                 "head": "Separate look clips return to neutral. Runtime may drive/clamp head yaw/pitch directly instead.",
                                 "light": "Lamp texels/mask only describe appearance. No beam is included, and actual world illumination is not provided by these files."}}
    write_json("robot_model.json", model)
    write_json("robot_animations.json", {"schema": "codeling.robot.animations.v1", "rotation_unit": "degrees", "animations": animations})
    write_json("robot_uv_layout.json", {"texture_size": [64,64], "faces": [
        {"bone": b["name"], "cube": c["name"], "face": f, **d}
        for b in BONES for c in b["cubes"] for f,d in c["faces"].items()]})
    for index, variant in enumerate(PALETTES):
        write_json(f"codeling_robot_{variant}.bbmodel", bbmodel(model,index))
    # Enlarged contact sheet is a labelled atlas inspection aid, not a runtime texture.
    contact = Image.new("RGB", (3*272,300), "#E7E3DC")
    draw = ImageDraw.Draw(contact)
    for index,variant in enumerate(PALETTES):
        image = Image.open(ROOT/f"robot_{variant}.png").resize((256,256),Image.Resampling.NEAREST)
        contact.paste(image,(index*272+8,28),image)
        draw.text((index*272+8,8),variant,fill="#202A30")
    contact.save(ROOT/"texture-atlases-preview.png")
    validate(model)


if __name__ == "__main__":
    main()
