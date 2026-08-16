import fs from "node:fs/promises";
import path from "node:path";

const source = process.argv[2];
if (!source) throw new Error("Usage: node tools/import-pokemon-regions.mjs <pokedex-data-dir>");

const groups = {
  kanto: { label: "关都", files: ["关都"] },
  johto: { label: "城都", files: ["城都"] },
  hoenn: { label: "丰缘", files: ["丰缘"] },
  sinnoh: { label: "神奥", files: ["神奥"] },
  unova: { label: "合众", files: ["合众"] },
  kalos: { label: "卡洛斯", files: ["卡洛斯-中央", "卡洛斯-山岳", "卡洛斯-海岸"] },
  alola: { label: "阿罗拉", files: ["阿罗拉", "阿罗拉-美乐美乐", "阿罗拉-阿卡拉", "阿罗拉-乌拉乌拉", "阿罗拉-波尼"] },
  hisui: { label: "洗翠", files: ["洗翠"] },
  galar: { label: "伽勒尔", files: ["伽勒尔", "伽勒尔-铠岛", "伽勒尔-王冠雪原"] },
  paldea: { label: "帕底亚", files: ["帕底亚", "帕底亚-北上", "帕底亚-蓝莓"] },
  lumiose: { label: "密阿雷", files: ["密阿雷", "密阿雷-超级进化", "密阿雷-异次元"] },
};

const catalog = JSON.parse(await fs.readFile("app/src/main/assets/pokemon/catalog.json", "utf8"));
const catalogIds = new Set(catalog.records.map(record => record.id));
const regions = [];
for (const [id, group] of Object.entries(groups)) {
  const dexIds = new Set();
  for (const file of group.files) {
    const entries = JSON.parse(await fs.readFile(path.join(source, `${file}.json`), "utf8"));
    for (const entry of entries) dexIds.add(String(entry.national_id).padStart(4, "0"));
  }
  const ids = [...dexIds].sort();
  const unmapped = ids.filter(value => !catalogIds.has(value));
  regions.push({ id, label: group.label, dexIds: ids });
  console.log(`${group.label}: ${ids.length} IDs, ${unmapped.length} unmapped${unmapped.length ? ` (${unmapped.join(", ")})` : ""}`);
}
await fs.writeFile("app/src/main/assets/pokemon/game_regions.json", JSON.stringify({ schemaVersion: 1, regions }), "utf8");
