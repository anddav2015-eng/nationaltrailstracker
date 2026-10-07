// Great Glen Way – landmarks are [name, miles from start, optional note].
// To record a finished stage, add  date: "d Mon yyyy", steps: 12345  to it in the stages list.
window.TRAILS = window.TRAILS || {};
window.TRAILS["great-glen-way"] = {
  name: "Great Glen Way",
  route: "Fort William to Inverness",
  startLabel: "Fort William",
  endLabel: "Inverness",
  colour: "#355c7d",
  subtitle: "Fort William to Inverness along the Caledonian Canal and Loch Ness",
  footer: "79 miles in total (nationaltrails.uk). Section-end places are taken from published section distances and rounded to fit that total, so treat them as estimates (&plusmn;1 mile). Intermediate landmarks are estimated positions between them (&plusmn;0.5 mile). Mileages assume the high routes between Fort Augustus and Drumnadrochit. GPS distances on the ground will read a little longer.",
  landmarks: [
    ["Fort William (Old Inverlochy Fort)", 0, "start; station"],
    ["Banavie (Neptune's Staircase)", 3, "station"],
    ["Gairlochy", 10.5],
    ["Clunes", 13.5],
    ["Kilfinnan", 21],
    ["Laggan Locks", 23.5, "buses on A82"],
    ["Aberchalder (Bridge of Oich)", 30, "buses on A82"],
    ["Fort Augustus", 35, "buses"],
    ["Invermoriston", 43, "buses"],
    ["Grotaig", 51.5],
    ["Drumnadrochit", 58.5, "buses"],
    ["Abriachan", 67.5, "campsite and cafe"],
    ["Inverness Castle", 79, "finish; station"],
  ],
  stages: [
    { from: "Fort William (Old Inverlochy Fort)", to: "Gairlochy", note: "Flat; canal towpath" },
    { from: "Gairlochy", to: "Laggan Locks", note: "Forest tracks along Loch Lochy" },
    { from: "Laggan Locks", to: "Fort Augustus", note: "Old railway and towpath beside Loch Oich" },
    { from: "Fort Augustus", to: "Invermoriston", note: "High route above Loch Ness" },
    { from: "Invermoriston", to: "Drumnadrochit", note: "High route; the hilliest day" },
    { from: "Drumnadrochit", to: "Abriachan", note: "Climbs out of Glen Urquhart" },
    { from: "Abriachan", to: "Inverness Castle", note: "Moorland, then forest down to Inverness" },
  ],
};
