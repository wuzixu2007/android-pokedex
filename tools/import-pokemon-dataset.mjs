import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";

const [dataDir, imageDir] = process.argv.slice(2);
if (!dataDir || !imageDir) {
  throw new Error("Usage: node tools/import-pokemon-dataset.mjs <pokemon-data-dir> <home-image-dir>");
}

const assetRoot = path.resolve("app/src/main/assets/pokemon");
const imageOutput = path.join(assetRoot, "images");
const detailOutput = path.join(assetRoot, "details");
const evolutionOutput = path.join(assetRoot, "evolution");
const itemOutput = path.join(assetRoot, "items");
const sourceImageRoot = path.dirname(path.resolve(imageDir));
const dreamDir = path.join(sourceImageRoot, "dream");
const itemDir = path.join(sourceImageRoot, "items");
await fs.mkdir(imageOutput, { recursive: true });
await fs.mkdir(detailOutput, { recursive: true });
await fs.mkdir(evolutionOutput, { recursive: true });
await fs.mkdir(itemOutput, { recursive: true });

const dreamFiles = new Set(await fs.readdir(dreamDir));
const itemFiles = await fs.readdir(itemDir);
const itemByName = itemFiles
  .filter(file => /\.png$/i.test(file))
  .map(file => ({ file, name: path.basename(file, path.extname(file)) }))
  .sort((a, b) => b.name.length - a.name.length);
const referencedDream = new Set();
const referencedItems = new Set();

function attachEvolutionAssets(species) {
  const attach = entry => {
    if (entry?.image && dreamFiles.has(entry.image)) {
      entry.image_asset = `pokemon/evolution/${entry.image}`;
      referencedDream.add(entry.image);
    }
    const condition = String(entry?.text ?? "");
    const item = itemByName.find(candidate => candidate.name.length >= 2 && condition.includes(candidate.name));
    if (item) {
      entry.item_asset = `pokemon/items/${item.file}`;
      referencedItems.add(item.file);
    }
  };
  for (const chain of species.evolution_chains ?? []) for (const entry of chain ?? []) attach(entry);
  for (const entry of species.mega_evolution ?? []) attach(entry);
  for (const entry of species.gigantamax_evolution ?? []) attach(entry);
}

const cleanName = value => String(value ?? "")
  .replace(/（([^）]+)）/g, "-$1")
  .replace(/\(([^)]+)\)/g, "-$1")
  .replace(/-{2,}/g, "-")
  .replace(/^-|-$/g, "");
const compact = value => cleanName(value).replace(/[\s·\-_/]/g, "");
const statNumber = value => {
  const parsed = Number.parseInt(String(value ?? ""), 10);
  return Number.isFinite(parsed) ? parsed : 0;
};
const assetName = file => file ? file.replace(/\.png$/i, ".webp") : null;
let validSourceImages = new Set();
const assetPath = file => file && validSourceImages.has(file) ? `pokemon/images/${assetName(file)}` : null;
const stableKey = (id, name) => `p${id}_${crypto.createHash("sha1").update(name).digest("hex").slice(0, 10)}`;
const excludedPikachuForms = new Set(["换装皮卡丘", "搭档皮卡丘"]);
const excludedPikachuImage = () => false;

function canonicalName(speciesName, formName, index) {
  const cleaned = cleanName(formName);
  if (cleaned === `超极巨化${speciesName}`) return `${cleanName(speciesName)}-超极巨化`;
  if (index === 0) return cleaned.startsWith(`${cleanName(speciesName)}-`) ? cleaned : cleanName(speciesName);
  const descriptivePrefix = /^(阿罗拉|伽勒尔|洗翠|帕底亚|超级|超极巨化|原始)/;
  if (cleaned.includes(speciesName) || descriptivePrefix.test(cleaned)) return cleaned;
  return `${cleanName(speciesName)}-${cleaned}`;
}

function statScore(formName, statLabel) {
  const form = compact(formName);
  const label = compact(statLabel);
  let score = 0;
  for (const token of ["阿罗拉", "伽勒尔", "洗翠", "帕底亚", "超级", "超极巨", "原始", "攻击", "防御", "速度", "起源", "达摩", "天空", "灵兽", "化身"]) {
    if (form.includes(token) && label.includes(token)) score += 8;
    else if (form.includes(token) !== label.includes(token)) score -= 3;
  }
  if (form.includes(label) || label.includes(form)) score += 12;
  return score;
}

function statsFor(species, form, index) {
  const stats = species.stats ?? [];
  if (!stats.length) return {};
  if (/超极巨/.test(form.name)) return stats[0]?.data ?? {};
  if (index === 0) {
    return (stats.find(item => /一般|普通|第\w+世代起/.test(item.form)) ?? stats[0]).data ?? {};
  }
  const ranked = stats.map(item => ({ item, score: statScore(form.name, item.form) })).sort((a, b) => b.score - a.score);
  return (ranked[0]?.score > 0 ? ranked[0].item : stats[0]).data ?? {};
}

function selectAppearanceForm(species, forms, homeImage) {
  const exact = forms.findIndex(form => form.image === homeImage.image);
  if (exact >= 0) return exact;
  const name = compact(homeImage.name);
  if (species.pokedex_id === "0025" && name.includes("帽子")) {
    const index = forms.findIndex(form => form.name.includes("帽子"));
    if (index >= 0) return index;
  }
  let bestIndex = 0;
  let bestScore = 0;
  forms.forEach((form, index) => {
    if (index === 0) return;
    const formTokens = compact(form.name).replace(compact(species.name_zh), "");
    const score = formTokens && (name.includes(formTokens) || formTokens.includes(name.replace(compact(species.name_zh), ""))) ? formTokens.length : 0;
    if (score > bestScore) {
      bestScore = score;
      bestIndex = index;
    }
  });
  return bestIndex;
}

const sourceImages = (await fs.readdir(imageDir)).filter(file => file.endsWith(".png"));
const imageChecks = await Promise.all(sourceImages.map(async file => {
  const bytes = await fs.readFile(path.join(imageDir, file));
  const isWebp = bytes.length >= 16 && bytes.toString("ascii", 0, 4) === "RIFF" && bytes.toString("ascii", 8, 12) === "WEBP";
  const declaredLength = isWebp ? bytes.readUInt32LE(4) + 8 : 0;
  return [file, isWebp && declaredLength <= bytes.length];
}));
validSourceImages = new Set(imageChecks.filter(([, valid]) => valid).map(([file]) => file));

const files = (await fs.readdir(dataDir)).filter(file => file.endsWith(".json")).sort();
const records = [];
const aliases = {};
const canonicalNames = new Set();

for (const file of files) {
  const raw = await fs.readFile(path.join(dataDir, file), "utf8");
  const species = JSON.parse(raw);
  const id = String(species.pokedex_id).padStart(4, "0");
  for (const form of species.forms ?? []) delete form.base_points;
  attachEvolutionAssets(species);
  await fs.writeFile(path.join(detailOutput, `${id}.json`), JSON.stringify(species), "utf8");
  const forms = (species.forms ?? []).filter(form => !excludedPikachuForms.has(cleanName(form.name)));
  const appearancesByForm = forms.map(() => []);
  for (const homeImage of (species.home_images ?? []).filter(image => !excludedPikachuImage(image.image) && !excludedPikachuImage(image.shiny ?? ""))) {
    const formIndex = selectAppearanceForm(species, forms, homeImage);
    const base = cleanName(species.name_zh);
    const label = cleanName(homeImage.name).replace(new RegExp(`^${base}-?`), "") || "默认";
    appearancesByForm[formIndex].push({
      label,
      imageAsset: assetPath(homeImage.image),
      shinyImageAsset: assetPath(homeImage.shiny),
    });
  }

  forms.forEach((form, index) => {
    const name = canonicalName(species.name_zh, form.name, index);
    if (canonicalNames.has(name)) throw new Error(`Duplicate canonical name: ${name}`);
    canonicalNames.add(name);
    const sourceStats = statsFor(species, form, index);
    const appearances = appearancesByForm[index];
    const mappedPrimary = species.home_images?.find(item => item.image === form.image);
    if (mappedPrimary && !appearances.some(item => item.imageAsset === assetPath(mappedPrimary.image))) {
      appearances.unshift({ label: "默认", imageAsset: assetPath(mappedPrimary.image), shinyImageAsset: assetPath(mappedPrimary.shiny) });
    }
    const primaryAppearance = appearances[0] ?? appearancesByForm[0][0] ?? {
      label: "默认",
      imageAsset: `pokemon/images/p${id}_v00.png`,
      shinyImageAsset: null,
    };
    if (appearances.length === 0) appearances.push(primaryAppearance);
    const normalAbilities = (form.abilities ?? []).filter(item => !item.is_hidden).map(item => item.name);
    const hiddenAbilities = (form.abilities ?? []).filter(item => item.is_hidden).map(item => item.name);
    const record = {
      key: stableKey(id, name),
      id,
      sourceFormName: form.name,
      nameZh: name,
      nameJa: species.name_ja ?? "",
      nameEn: species.name_en ?? "",
      types: form.types ?? [],
      attributeLabel: `${(form.types ?? []).join("属性和")}属性宝可梦`,
      category: form.category ?? "",
      height: form.height ?? "",
      weight: form.weight ?? "",
      abilities: { normal: normalAbilities, hidden: hiddenAbilities },
      stats: {
        hp: statNumber(sourceStats.hp),
        attack: statNumber(sourceStats.attack),
        defense: statNumber(sourceStats.defense),
        specialAttack: statNumber(sourceStats.sp_attack),
        specialDefense: statNumber(sourceStats.sp_defense),
        speed: statNumber(sourceStats.speed),
      },
      description: species.description ?? "",
      profile: species.profile ?? "",
      imageAsset: primaryAppearance.imageAsset,
      shinyImageAsset: primaryAppearance.shinyImageAsset,
      appearances,
      detailsAsset: `pokemon/details/${id}.json`,
    };
    records.push(record);

    const aliasValues = new Set([
      form.name,
      `${species.name_zh}-${form.name}`,
      `${species.name_zh}(${form.name})`,
      `${species.name_zh}（${form.name}）`,
      ...(appearances.map(item => item.label === "默认" ? species.name_zh : `${species.name_zh}-${item.label}`)),
      ...(appearances.flatMap(item => item.label === "默认" ? [] : [
        `${species.name_zh}(${item.label})`,
        `${species.name_zh}（${item.label}）`,
      ])),
    ]);
    for (const alias of aliasValues) {
      const cleaned = cleanName(alias);
      for (const candidate of new Set([alias, cleaned])) {
        if (candidate && candidate !== name && aliases[candidate] == null) aliases[candidate] = name;
      }
    }
  });
}

if (files.length !== 1025) throw new Error(`Expected 1025 data files, got ${files.length}`);
if (records.length !== 1318) throw new Error(`Expected 1318 forms after excluded Pikachu forms, got ${records.length}`);

for (const file of sourceImages) {
  const output = path.join(imageOutput, assetName(file));
  if (excludedPikachuImage(file)) await fs.rm(output, { force: true });
  else await fs.copyFile(path.join(imageDir, file), output);
}
const referenced = new Set(records.flatMap(record => [
  record.imageAsset,
  record.shinyImageAsset,
  ...record.appearances.flatMap(item => [item.imageAsset, item.shinyImageAsset]),
].filter(Boolean).map(value => path.basename(value))));
for (const file of referenced) {
  await fs.access(path.join(imageOutput, file));
}
for (const file of referencedDream) await fs.copyFile(path.join(dreamDir, file), path.join(evolutionOutput, file));
for (const file of referencedItems) await fs.copyFile(path.join(itemDir, file), path.join(itemOutput, file));

const catalog = {
  schemaVersion: 4,
  source: "pokemon-dataset-zh forms + home images",
  records,
  aliases,
};
await fs.writeFile(path.join(assetRoot, "catalog.json"), JSON.stringify(catalog), "utf8");
console.log(JSON.stringify({
  dataFiles: files.length,
  records: records.length,
  aliases: Object.keys(aliases).length,
  images: sourceImages.filter(file => !excludedPikachuImage(file)).length,
  invalidImages: sourceImages.length - validSourceImages.size,
  referenced: referenced.size,
  evolutionImages: referencedDream.size,
  evolutionItems: referencedItems.size,
}));
