/** Builds compact validated Android catalog assets. / 生成紧凑且经过校验的 Android 图鉴资源。 */
import fs from "node:fs";
import path from "node:path";

const defaultDatasetRoot =
  "C:/Users/jy420/Downloads/pokemon-dataset-zh-main/pokemon-dataset-zh-main";
const defaultOutputRoot = path.resolve("app/src/main/assets/pokemon");

const datasetRoot = path.resolve(process.argv[2] ?? defaultDatasetRoot);
const outputRoot = path.resolve(process.argv[3] ?? defaultOutputRoot);
const imageOutputRoot = path.join(outputRoot, "images");

const readJson = (file) => JSON.parse(fs.readFileSync(file, "utf8"));
const normalize = (value) =>
  value
    .normalize("NFKC")
    .replace(/[\s\-\u2014_\uFF08\uFF09()]/g, "")
    .replace(/\u7684\u6837\u5b50/g, "");

const national = readJson(path.join(datasetRoot, "data/pokedex/national.json"));
const simple = readJson(path.join(datasetRoot, "data/simple_pokedex.json"));
const detailsRoot = path.join(datasetRoot, "data/pokemon");
const officialImagesRoot = path.join(datasetRoot, "data/images/official");

const simpleById = new Map(simple.map((entry) => [entry.index, entry]));
const detailFileById = new Map(
  fs
    .readdirSync(detailsRoot)
    .filter((name) => name.endsWith(".json"))
    .map((name) => [name.slice(0, 4), name]),
);
const detailById = new Map();
const variantById = new Map();

function detailFor(id) {
  if (detailById.has(id)) return detailById.get(id);
  const fileName = detailFileById.get(id);
  if (!fileName) throw new Error(`Missing detail JSON for ${id}`);
  const detail = readJson(path.join(detailsRoot, fileName));
  detailById.set(id, detail);
  return detail;
}

function selectForm(entry, detail) {
  const forms = detail.forms ?? [];
  if (forms.length === 0) throw new Error(`No forms for ${entry.id} ${entry.name}`);

  if (entry.name === detail.name_zh) return forms[0];

  const baseName = normalize(detail.name_zh);
  const descriptor = normalize(entry.name).replace(baseName, "");
  const descriptorMatches = forms.filter(
    (form) => normalize(form.name).replace(baseName, "") === descriptor,
  );
  if (descriptorMatches.length === 1) return descriptorMatches[0];

  const typeMatches = forms.filter(
    (form) => JSON.stringify(form.types) === JSON.stringify(entry.types),
  );
  if (typeMatches.length === 1) return typeMatches[0];

  throw new Error(
    `Ambiguous form mapping for ${entry.id} ${entry.name}: ` +
      forms.map((form) => form.name).join(", "),
  );
}

function selectStats(detail, form) {
  const stats = detail.stats ?? [];
  if (stats.length === 0) throw new Error(`No stats for ${detail.pokedex_id} ${detail.name_zh}`);

  const formIndex = (detail.forms ?? []).indexOf(form);
  const baseName = normalize(detail.name_zh);
  const descriptor = normalize(form.name).replace(baseName, "");
  if (descriptor) {
    const descriptorMatches = stats.filter((entry) => {
      const statForm = normalize(entry.form ?? "");
      return statForm.includes(descriptor) || descriptor.includes(statForm);
    });
    if (descriptorMatches.length === 1) return descriptorMatches[0].data;
  }

  // Most files keep form stats in the same order as forms. This is also the
  // only deterministic fallback for special forms whose stat label differs.
  const indexed = stats[formIndex]?.data;
  if (indexed) return indexed;
  return stats[0].data;
}

function requiredStat(data, key, detail) {
  const value = Number(data?.[key]);
  if (!Number.isInteger(value) || value < 0 || value > 255) {
    throw new Error(`Invalid ${key} stat for ${detail.name_zh}: ${data?.[key]}`);
  }
  return value;
}

function shortDescription(detail) {
  const generations = detail.pokedex_entries ?? [];
  for (let generationIndex = generations.length - 1; generationIndex >= 0; generationIndex--) {
    const versions = generations[generationIndex]?.versions ?? [];
    for (let versionIndex = versions.length - 1; versionIndex >= 0; versionIndex--) {
      const text = versions[versionIndex]?.text?.replace(/\s+/g, " ").trim();
      if (text) return text;
    }
  }
  return detail.description?.replace(/\s+/g, " ").trim() ?? "";
}

function normalizeProfile(detail) {
  const profile = detail.profile?.replace(/\r\n?/g, "\n").trim() ?? "";
  if (!profile) throw new Error(`Missing profile for ${detail.pokedex_id} ${detail.name_zh}`);
  return profile
    .split("\n")
    .map((paragraph) => paragraph.replace(/\s+/g, " ").trim())
    .filter(Boolean)
    .join("\n");
}

function attributeLabel(types, entry) {
  if (!Array.isArray(types) || types.length === 0) {
    throw new Error(`Missing types for ${entry.id} ${entry.name}`);
  }
  return `${types.map((type) => `${type}属性`).join("和")}宝可梦`;
}

fs.mkdirSync(imageOutputRoot, { recursive: true });

const records = national.map((entry) => {
  const detail = detailFor(entry.id);
  const form = selectForm(entry, detail);
  const stats = selectStats(detail, form);
  const variant = variantById.get(entry.id) ?? 0;
  variantById.set(entry.id, variant + 1);

  const assetKey = `p${entry.id}_v${String(variant).padStart(2, "0")}`;
  const imageName = `${assetKey}.png`;
  const imageSource = path.join(officialImagesRoot, form.image);
  if (!fs.existsSync(imageSource)) {
    throw new Error(`Missing image for ${entry.id} ${entry.name}: ${form.image}`);
  }
  fs.copyFileSync(imageSource, path.join(imageOutputRoot, imageName));

  const simpleEntry = simpleById.get(entry.id);
  const types = form.types ?? entry.types ?? [];
  return {
    key: assetKey,
    id: entry.id,
    nameZh: entry.name,
    nameJa: detail.name_ja ?? simpleEntry?.name_jp ?? "",
    nameEn: simpleEntry?.name_en ?? detail.name_en ?? "",
    types,
    attributeLabel: attributeLabel(types, entry),
    category: form.category ?? "",
    height: form.height ?? "",
    weight: form.weight ?? "",
    ability:
      form.abilities?.find((ability) => !ability.is_hidden)?.name ??
      form.abilities?.[0]?.name ??
      "",
    stats: {
      hp: requiredStat(stats, "hp", detail),
      attack: requiredStat(stats, "attack", detail),
      defense: requiredStat(stats, "defense", detail),
      specialAttack: requiredStat(stats, "sp_attack", detail),
      specialDefense: requiredStat(stats, "sp_defense", detail),
      speed: requiredStat(stats, "speed", detail),
    },
    description: shortDescription(detail),
    profile: normalizeProfile(detail),
    imageAsset: `pokemon/images/${imageName}`,
  };
});

const names = records.map((record) => record.nameZh);
if (records.length !== 1082) throw new Error(`Expected 1082 records, got ${records.length}`);
if (new Set(names).size !== records.length) throw new Error("Canonical names are not unique");
if (names.filter((name) => name === "\u767e\u53d8\u602a").length !== 1) {
  throw new Error("Expected exactly one Ditto record");
}
if (names.some((name) => /[*\uFF0A]/.test(name))) {
  throw new Error("Canonical names contain non-standard asterisk suffixes");
}

const catalog = {
  schemaVersion: 3,
  source: "pokemon-dataset-zh national.json",
  records,
};
fs.writeFileSync(path.join(outputRoot, "catalog.json"), `${JSON.stringify(catalog)}\n`, "utf8");

const imageBytes = records.reduce(
  (total, record) => total + fs.statSync(path.join(outputRoot, record.imageAsset.replace("pokemon/", ""))).size,
  0,
);
console.log(
  JSON.stringify(
    {
      records: records.length,
      uniqueNames: new Set(names).size,
      imageBytes,
      outputRoot,
    },
    null,
    2,
  ),
);
