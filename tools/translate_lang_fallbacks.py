from __future__ import annotations

import argparse
import json
from pathlib import Path


def find_repo_root(start: Path) -> Path:
    current = start.resolve()
    for candidate in [current, *current.parents]:
        if (candidate / 'settings.gradle.kts').exists() or (candidate / 'gradlew').exists() or (candidate / '.git').exists():
            return candidate
    return current


def find_lang_dir(repo_root: Path) -> Path:
    possible_roots = [
        repo_root / 'shared' / 'src' / 'main' / 'resources' / 'assets',
        repo_root / 'src' / 'main' / 'resources' / 'assets',
        repo_root / 'shared' / 'src' / 'main' / 'generated' / 'assets',
        repo_root / 'src' / 'main' / 'generated' / 'assets',
        repo_root / 'test' / 'src' / 'main' / 'resources' / 'assets',
        repo_root / 'test' / 'src' / 'main' / 'generated' / 'assets',
        repo_root / 'fabric' / 'run' / 'config' / 'hydraulic' / 'storage',
        repo_root / 'fabric' / 'run' / 'config',
    ]

    ranked_dirs: list[tuple[int, Path]] = []

    for path in possible_roots:
        if path.exists():
            for lang_dir in sorted(p for p in path.rglob('lang') if p.is_dir()):
                lower = str(lang_dir).lower()
                priority = 1000
                if 'src/main/generated' in lower or 'shared/src/main/generated' in lower:
                    priority = 0
                elif 'src/main/resources' in lower or 'shared/src/main/resources' in lower:
                    priority = 10
                elif 'fabric/run/config' in lower:
                    priority = 100
                if 'build' in lower or 'bin' in lower:
                    priority += 200
                if 'datagen' in lower:
                    priority += 300
                if 'runtimeresources' in lower:
                    priority += 400
                ranked_dirs.append((priority, lang_dir))

    if ranked_dirs:
        return min(ranked_dirs, key=lambda item: (item[0], str(item[1])))[1]

    lang_dirs = sorted(p for p in repo_root.rglob('lang') if p.is_dir())
    if lang_dirs:
        ranked_fallback = []
        for lang_dir in lang_dirs:
            lower = str(lang_dir).lower()
            priority = 1000
            if 'build' in lower or 'bin' in lower:
                priority += 200
            if 'datagen' in lower:
                priority += 300
            if 'runtimeresources' in lower:
                priority += 400
            ranked_fallback.append((priority, lang_dir))
        return min(ranked_fallback, key=lambda item: (item[0], str(item[1])))[1]

    raise FileNotFoundError(f'No language directory was found under {repo_root}')


def read_json(path: Path) -> dict:
    with path.open('r', encoding='utf-8') as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ValueError(f'{path} does not contain a JSON object at the top level.')
    return data


def write_json(path: Path, payload: dict) -> None:
    with path.open('w', encoding='utf-8', newline='\n') as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write('\n')


def resolve_source_locale(lang_dir: Path, preferred: str) -> Path:
    preferred_path = lang_dir / preferred
    if preferred_path.exists():
        return preferred_path

    for candidate in sorted(lang_dir.glob('*.json')):
        name = candidate.name.lower()
        if name in {'en_us.json', 'en.json'} or name.startswith('en_'):
            return candidate

    json_files = sorted(lang_dir.glob('*.json'))
    if not json_files:
        raise FileNotFoundError(f'No JSON locale files found in {lang_dir}')
    return json_files[0]


def sync_locale_files(repo_root: Path, source_locale_name: str, prune_extra_keys: bool = True) -> tuple[int, int]:
    lang_dir = find_lang_dir(repo_root)
    source_file = resolve_source_locale(lang_dir, source_locale_name)
    source_obj = read_json(source_file)
    source_order = list(source_obj.keys())

    created = 0
    updated = 0

    for locale_file in sorted(lang_dir.glob('*.json')):
        if locale_file == source_file:
            continue

        locale_obj = read_json(locale_file)
        output = {}

        for key in source_order:
            if key in locale_obj:
                output[key] = locale_obj[key]
            else:
                output[key] = source_obj[key]

        if not prune_extra_keys:
            for key, value in locale_obj.items():
                output.setdefault(key, value)
        else:
            output = {key: output[key] for key in source_order if key in output}

        if locale_obj != output:
            write_json(locale_file, output)
            updated += 1

    return created, updated


def main() -> int:
    parser = argparse.ArgumentParser(description='Sync locale JSON files to the repo source locale.')
    parser.add_argument('--repo-root', type=Path, default=Path.cwd())
    parser.add_argument('--source-locale', default='en_us.json')
    parser.add_argument('--keep-extra-keys', action='store_true', help='Keep locale keys that are not in the source locale.')
    args = parser.parse_args()

    repo_root = find_repo_root(args.repo_root)
    prune_extra = not args.keep_extra_keys
    created, updated = sync_locale_files(repo_root, args.source_locale, prune_extra)
    print(f'Repository root: {repo_root}')
    print(f'Locales scanned in {find_lang_dir(repo_root)}')
    print(f'Created: {created}')
    print(f'Updated: {updated}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
        "Sound Scape (7.1)": "Tunog na tanawin (7.1)",
        "Mode": "Moda",
        "Debug key": "Susi ng debug",
        "Target": "Target na lokasyon",
        "Accel bump": "Bugso ng pagbilis",
    },
}

PLACEHOLDER_PATTERN = re.compile(r"%[0-9]*\$?[sd]")
SPLIT_TOKEN = "<<<BST_SPLIT>>>"


def load_json(path: Path) -> dict[str, str]:
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def write_json(path: Path, data: dict[str, str]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def same_percent(source: dict[str, str], target: dict[str, str], ordered_keys: list[str]) -> float:
    same = sum(1 for key in ordered_keys if target.get(key) == source[key])
    return round((same / len(ordered_keys)) * 100, 1)


def chunked(values: list[str], size: int) -> list[list[str]]:
    return [values[index:index + size] for index in range(0, len(values), size)]


def normalize_translation(source_text: str, translated_text: str) -> str:
    source_placeholders = PLACEHOLDER_PATTERN.findall(source_text)
    translated_placeholders = PLACEHOLDER_PATTERN.findall(translated_text)
    if source_placeholders == translated_placeholders:
        return translated_text
    repaired = PLACEHOLDER_PATTERN.sub("{}", translated_text)
    if repaired.count("{}") == len(source_placeholders):
        for placeholder in source_placeholders:
            repaired = repaired.replace("{}", placeholder, 1)
        return repaired
    if source_placeholders:
        return f"{translated_text} {' '.join(source_placeholders)}"
    return translated_text


def request_translation(target_language: str, text: str) -> list:
    query = urllib.parse.urlencode(
        {
            "client": "gtx",
            "sl": "en",
            "tl": target_language,
            "dt": "t",
            "q": text,
        }
    )
    url = f"https://translate.googleapis.com/translate_a/single?{query}"
    context = ssl._create_unverified_context()
    with urllib.request.urlopen(url, context=context, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def translate_batch(target_language: str, texts: list[str]) -> list[str]:
    if not texts:
        return []
    joined_text = f"\n{SPLIT_TOKEN}\n".join(texts)
    for attempt in range(3):
        try:
            payload = request_translation(target_language, joined_text)
            translated = "".join(part[0] for part in payload[0])
            translated = translated.split(f"\n{SPLIT_TOKEN}\n")
            if len(translated) != len(texts):
                return [translate_batch(target_language, [text])[0] for text in texts]
            return [normalize_translation(source, result) for source, result in zip(texts, translated)]
        except Exception:
            if attempt == 2:
                raise
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError("Unreachable retry state")


def translate_locale(en_data: dict[str, str], locale_path: Path, threshold: float, chunk_size: int) -> bool:
    locale_name = locale_path.name
    if locale_name in EXEMPT_LOCALES:
        return False

    ordered_keys = list(en_data.keys())
    locale_data = load_json(locale_path)
    if same_percent(en_data, locale_data, ordered_keys) < threshold:
        return False

    language = LOCALE_TO_LANGUAGE.get(locale_name)
    if not language:
        raise KeyError(f"No translation target configured for {locale_name}")

    keys_to_translate = [
        key for key in ordered_keys
        if locale_data.get(key, en_data[key]) == en_data[key]
    ]
    if not keys_to_translate:
        return False

    forced_translations = FORCED_TRANSLATIONS.get(locale_name, {})
    translated_values: dict[str, str] = {}
    keys_for_service: list[str] = []
    for key in keys_to_translate:
        source_text = en_data[key]
        if source_text in forced_translations:
            translated_values[key] = forced_translations[source_text]
        else:
            keys_for_service.append(key)

    for chunk in chunked(keys_for_service, chunk_size):
        source_texts = [en_data[key] for key in chunk]
        translated_texts = translate_batch(language, source_texts)
        translated_values.update(dict(zip(chunk, translated_texts)))
        time.sleep(0.2)

    updated = {key: translated_values.get(key, locale_data.get(key, en_data[key])) for key in ordered_keys}
    write_json(locale_path, updated)
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="Translate still-English locale entries from en_us.json")
    parser.add_argument("--lang-dir", default="src/main/resources/assets/bassshakertelemetry/lang")
    parser.add_argument("--threshold", type=float, default=15.0)
    parser.add_argument("--chunk-size", type=int, default=20)
    parser.add_argument("--only", nargs="*", default=[])
    args = parser.parse_args()

    lang_dir = Path(args.lang_dir)
    en_path = lang_dir / "en_us.json"
    en_data = load_json(en_path)
    only = set(args.only)

    updated_files: list[str] = []
    for locale_path in sorted(lang_dir.glob("*.json")):
        if locale_path.name == "en_us.json":
            continue
        if only and locale_path.name not in only:
            continue
        changed = translate_locale(en_data, locale_path, args.threshold, args.chunk_size)
        if changed:
            updated_files.append(locale_path.name)
            print(f"Translated fallback entries in {locale_path.name}")

    if not updated_files:
        print("No locale files required translation updates.")
        return 0

    print(f"Updated {len(updated_files)} locale files.")
    return 0


if __name__ == "__main__":
    sys.exit(main())