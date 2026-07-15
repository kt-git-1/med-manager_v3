# デザイン検証記録

## 基準にしたデザイン

- 採用案（3枚目の縦型手順レイアウト＋1枚目の暖色系背景）: `/Users/kaito/.codex/generated_images/019f630f-5b32-7080-be96-4c6765d13d27/exec-3750483d-0978-4801-a512-878639afb40f.png`
- 実装対象: トップ、詳しい使い方、プライバシーポリシー、利用規約、サポート、メール確認完了
- 主な閲覧者: 高齢の本人を支える40〜60代の家族

## 比較条件と証跡

- パソコン: 1440 × 1024
- スマートフォン: 390 × 844
- 比較画像: `/Users/kaito/.codex/visualizations/2026/07/14/019f630f-5b32-7080-be96-4c6765d13d27/site-redesign-qa/guide-reference-vs-pass2.png`
- 使い方（パソコン）: `/Users/kaito/.codex/visualizations/2026/07/14/019f630f-5b32-7080-be96-4c6765d13d27/site-redesign-qa/guide-desktop-pass2.png`
- 使い方（スマートフォン）: `/Users/kaito/.codex/visualizations/2026/07/14/019f630f-5b32-7080-be96-4c6765d13d27/site-redesign-qa/guide-mobile-viewport.png`
- トップ（パソコン）: `/Users/kaito/.codex/visualizations/2026/07/14/019f630f-5b32-7080-be96-4c6765d13d27/site-redesign-qa/home-desktop-final.png`
- トップ（スマートフォン）: `/Users/kaito/.codex/visualizations/2026/07/14/019f630f-5b32-7080-be96-4c6765d13d27/site-redesign-qa/home-mobile-final2.png`
- 規約系の代表確認: `/Users/kaito/.codex/visualizations/2026/07/14/019f630f-5b32-7080-be96-4c6765d13d27/site-redesign-qa/privacy-desktop-final.png`

## 目視比較

- 文字: 深緑の大見出し、太い日本語見出し、18pxの本文を基準に統一した。
- 余白: セクション間を広く取り、読み進める順序が分かる縦型構成にした。
- 色: 暖かいクリーム背景、深緑の主要操作、家族モードを示すオレンジを全ページで共通化した。
- 画像: 作り物の画面ではなく、iPhone版アプリから取得した本人モード・家族モードの実画面を使用した。
- 内容: 英語の小見出しを廃止し、本人／家族／連携の説明を日本語で具体化した。

## 修正履歴

1. 初回比較では「使い方」ページの主見出しが4行になり、採用案より視線移動が増えていた。
2. 意味の切れ目で改行し、パソコンの文字サイズを48pxに調整した。
3. 2回目の比較で、見出しのまとまり、画面画像の位置、最初の手順への流れが改善したことを確認した。

## 動作・表示確認

- パソコン・スマートフォンとも横スクロールなし。
- トップ、使い方、プライバシー、メール確認完了でブラウザ警告・エラーなし。
- スマートフォンのメニュー開閉を確認。
- 実画面画像は縦横比を維持し、文字が読める大きさで表示。
- 型検査、静的解析、本番ビルドを通過。

## 残した差分

- 実装版の最上部は採用案よりやや高い。本文18pxと実画面の視認性を優先した意図的な差分であり、対象利用者にとっての読みやすさを損なわないため許容した。

final result: passed
