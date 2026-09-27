"""Generate the left-hand MATRIX clip; run with Python 3 from any directory.

Epic Fight 20.14.17 JsonAssetLoader reads row-major matrices (load + transpose),
then applies Blender->Minecraft Rx(-90) to Root and removes the joint bind pose.
X is the lateral axis in both coordinate systems. S=diag(-1,1,1,1) commutes
with that root correction. Reflect the full local matrix as S M S, NOT just
its translation, and exchange every _R/_L joint (including helper/Tool joints).
BIPED's paired bind bases follow this same reflection (export rounding aside).
No EFN metadata, events or trails are read. The original file is never written.
"""
import copy
import hashlib
import json
from pathlib import Path

BASE = Path(__file__).resolve().parents[1] / "src/main/resources/assets/bhspellsx/animmodels/animations/biped/spells"


def mirror(clip):
    result = copy.deepcopy(clip)
    names = {joint["name"] for joint in clip["animation"]}
    signs = (-1, 1, 1, 1)
    for joint in result["animation"]:
        name = joint["name"]
        target = name[:-2] + ("_L" if name.endswith("_R") else "_R") if name.endswith(("_R", "_L")) else name
        assert target in names, f"Missing counterpart: {name}"
        joint["name"] = target
        assert len(joint["time"]) == len(joint["transform"])
        for matrix in joint["transform"]:
            assert len(matrix) == 16
            for index in range(16):
                matrix[index] *= signs[index // 4] * signs[index % 4]
    return result


if __name__ == "__main__":
    source = BASE / "judgement_cut.json"
    before = source.read_bytes()
    clip = json.loads(before)
    result = mirror(clip)
    assert mirror(result) == clip, "Reflection must be an involution"
    # Retain compact per-frame arrays for reviewable diffs.
    parts = []
    for joint in result["animation"]:
        matrices = ",\n".join("        " + json.dumps(m) for m in joint["transform"])
        parts.append('    {\n      "name": ' + json.dumps(joint["name"]) + ',\n      "time": ' + json.dumps(joint["time"]) + ',\n      "transform": [\n' + matrices + '\n      ]\n    }')
    output = BASE / "judgement_cut_left.json"
    output.write_text('{\n  "animation": [\n' + ',\n'.join(parts) + '\n  ]\n}\n', encoding="utf-8")
    assert source.read_bytes() == before
    print(f"Generated {output.name}: {len(result['animation'])} joints; double mirror PASS; original SHA256 {hashlib.sha256(before).hexdigest()}")
