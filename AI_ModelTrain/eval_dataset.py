#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Batch-evaluate INT8 TFLite on ESC-format dataset; print recall + confusion matrix."""

from __future__ import annotations

import argparse
import csv
import sys
from collections import Counter
from pathlib import Path

import numpy as np

try:
    import tensorflow as tf
except ImportError:
    print("ERROR: tensorflow not found. Activate Model Zoo venv first.")
    sys.exit(1)


def load_labels(csv_path: Path) -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    with csv_path.open(encoding="utf-8") as f:
        for filename, category in csv.DictReader(f):
            rows.append((filename, category))
    return rows


def mel_preprocess(wav_path: Path, sample_rate: int = 16000) -> np.ndarray:
    """Minimal mel patch matching ai_config.h (64x96). Uses librosa if available."""
    import wave

    with wave.open(str(wav_path), "rb") as wf:
        pcm = np.frombuffer(wf.readframes(wf.getnframes()), dtype=np.int16).astype(np.float32)
        if wf.getframerate() != sample_rate:
            raise ValueError(f"{wav_path}: rate={wf.getframerate()}, want {sample_rate}")

    target_len = 400 + (96 - 1) * 160
    if len(pcm) < target_len:
        pcm = np.pad(pcm, (0, target_len - len(pcm)))
    else:
        pcm = pcm[:target_len]

    peak = max(float(np.max(np.abs(pcm))), 1.0)
    pcm = pcm / peak

    try:
        import librosa

        mel = librosa.feature.melspectrogram(
            y=pcm,
            sr=sample_rate,
            n_fft=512,
            hop_length=160,
            win_length=400,
            window="hann",
            center=False,
            n_mels=64,
            fmin=125,
            fmax=7500,
            power=1.0,
            htk=True,
        )
        mel = np.log(np.maximum(mel, 1e-6)).astype(np.float32)
        if mel.shape[1] != 96:
            if mel.shape[1] > 96:
                mel = mel[:, :96]
            else:
                mel = np.pad(mel, ((0, 0), (0, 96 - mel.shape[1])))
        return mel[..., np.newaxis]
    except ImportError:
        print("WARN: librosa missing; using raw normalized PCM fallback (less accurate)")
        return pcm.reshape(1, -1, 1).astype(np.float32)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--dataset", type=Path, required=True, help="ESC root with audio/ and meta/")
    parser.add_argument("--class-names", nargs="+", required=True)
    args = parser.parse_args()

    audio_dir = args.dataset / "audio"
    csv_path = args.dataset / "meta" / "labels.csv"
    if not args.model.is_file() or not csv_path.is_file():
        print("ERROR: model or labels.csv not found")
        sys.exit(1)

    class_to_idx = {c: i for i, c in enumerate(args.class_names)}
    interpreter = tf.lite.Interpreter(model_path=str(args.model))
    interpreter.allocate_tensors()
    in_det = interpreter.get_input_details()[0]
    out_det = interpreter.get_output_details()[0]

    y_true: list[int] = []
    y_pred: list[int] = []
    skipped = 0

    for filename, category in load_labels(csv_path):
        if category not in class_to_idx:
            skipped += 1
            continue
        wav = audio_dir / filename
        if not wav.is_file():
            skipped += 1
            continue
        try:
            x = mel_preprocess(wav)
            if x.shape != tuple(in_det["shape"]):
                x = np.resize(x, in_det["shape"]).astype(np.float32)
            interpreter.set_tensor(in_det["index"], np.expand_dims(x, 0))
            interpreter.invoke()
            logits = interpreter.get_tensor(out_det["index"])[0]
            pred = int(np.argmax(logits))
            y_true.append(class_to_idx[category])
            y_pred.append(pred)
        except Exception as exc:
            print(f"[SKIP] {filename}: {exc}")
            skipped += 1

    n = len(y_true)
    if n == 0:
        print("No samples evaluated")
        sys.exit(2)

    correct = sum(t == p for t, p in zip(y_true, y_pred))
    print(f"\n=== Eval: {args.model.name} ===")
    print(f"Samples: {n}  Skipped: {skipped}")
    print(f"Overall accuracy: {100.0 * correct / n:.2f}%")

    print("\nPer-class recall:")
    for i, name in enumerate(args.class_names):
        idxs = [j for j, t in enumerate(y_true) if t == i]
        if not idxs:
            print(f"  {name:12s}  (no samples)")
            continue
        hit = sum(1 for j in idxs if y_pred[j] == i)
        print(f"  {name:12s}  {hit}/{len(idxs)}  = {100.0 * hit / len(idxs):.1f}%")

    print("\nTop confusions (true -> pred):")
    confusions = Counter(
        (args.class_names[t], args.class_names[p])
        for t, p in zip(y_true, y_pred)
        if t != p
    )
    for (true_c, pred_c), cnt in confusions.most_common(10):
        print(f"  {true_c} -> {pred_c}: {cnt}")


if __name__ == "__main__":
    main()
