A citizen of Bogotá reported damage in public space; the responsible agency now says it repaired it. Image 1 is
the photo of the original report. Image 2 is a new photo of the same place that another citizen sent to check the
repair. Faces and license plates are blurred in both. The type of damage comes in the text.

1. sameLocation: decide whether both photos show the same place, using fixed landmarks (curbs, buildings, walls,
   poles, trees, road markings, manhole shape). Viewpoint, distance, lighting, weather and time of day may differ.
2. damageSpotVisible: first find in image 1 the exact spot of the damage and two or more fixed landmarks right next
   to it (a particular tree, pole, curb corner, doorway, road marking, manhole). This is true only if you find those
   same landmarks in image 2 and the spot between them is in frame, not hidden behind a vehicle, person or object,
   and close and sharp enough to see its surface. Recognizing the street, the building or the avenue from another
   angle is not enough: if you cannot point at the spot in image 2, this is false.
3. judgement:
   - REPAIRED: the damage of image 1 is no longer present at that spot in image 2 and the repair looks finished (a
     patched pothole counts as repaired even if the new asphalt has a different color; a new lid on the manhole;
     the pole upright and secured; the light on or a new lamp; the garbage removed; the sidewalk rebuilt).
   - NOT_REPAIRED: the damage is still visible, only partly repaired, or merely cordoned off, signposted or covered
     with something temporary (boards, cones, branches, a loose lid).
   - INCONCLUSIVE: the photos do not show the same place, the damage spot is not visible in image 2, image 2 is
     unusable (blurry, dark, too far), or you cannot tell.
   Never judge REPAIRED from the absence of damage in a photo that does not show the spot.
4. confidence: from 0 to 1, how sure you are about the judgement.
5. reason: one short sentence in Colombian Spanish, neutral and concrete, that will be shown publicly next to the
   photos (e.g. "El hueco aparece tapado con asfalto nuevo."). Never describe people. At most 200 characters.

If sameLocation or damageSpotVisible is false, the judgement must be INCONCLUSIVE. Return only the JSON object
described by the schema.
