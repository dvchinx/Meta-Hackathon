import json
from pathlib import Path

from app.sam.masks import output_text, parse_detections

FIXTURE = Path(__file__).parent / "fixtures" / "sam_pothole_960x720.json"


def load_fixture() -> dict:
    return json.loads(FIXTURE.read_text())


def test_parses_real_sam_output_into_full_frame_masks():
    detections = parse_detections(output_text(load_fixture()), frame_width=960, frame_height=720)

    assert [d.object_id for d in detections] == ["0", "1"]
    for d in detections:
        assert d.mask.shape == (720, 960)
        assert d.area_px > 0
        ys, xs = d.mask.nonzero()
        # every masked pixel lies inside the detection box
        assert xs.min() >= d.box.left and xs.max() < d.box.right
        assert ys.min() >= d.box.top and ys.max() < d.box.bottom


def test_masks_are_not_just_filled_boxes():
    detections = parse_detections(output_text(load_fixture()), 960, 720)

    for d in detections:
        box_area = (d.box.right - d.box.left) * (d.box.bottom - d.box.top)
        assert 0.2 < d.area_px / box_area < 0.98


def test_instance_masks_do_not_overlap():
    # SAM split the pothole into the deep hole and the worn area around it, and each pixel belongs to
    # one object only. The damage area is therefore the union of all detections.
    inner, outer = parse_detections(output_text(load_fixture()), 960, 720)

    assert inner.area_px < outer.area_px
    assert (inner.mask & outer.mask).sum() == 0


def test_empty_output_means_no_detections():
    assert parse_detections("", 960, 720) == []
    assert output_text({"output": []}) == ""
