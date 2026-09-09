package app.trainer.backend.link

internal fun linkPageHtml(
    title: String,
    heading: String,
    explanation: String,
    webUrl: String,
    appUrl: String,
    downloadUrl: String,
    highlight: String? = null,
): String = """
<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>$title</title>
<style>
body { font-family: -apple-system, system-ui, sans-serif; margin: 0; padding: 32px 20px;
       background: #f5f5f4; color: #1c1917; display: flex; justify-content: center; }
main { max-width: 420px; width: 100%; }
h1 { font-size: 24px; margin: 0 0 8px; }
p { color: #57534e; line-height: 1.5; margin: 0 0 24px; }
.code { font-family: ui-monospace, monospace; font-size: 32px; letter-spacing: 6px;
        background: #fff; border-radius: 12px; padding: 16px; text-align: center; margin-bottom: 24px; }
a.button { display: block; text-align: center; text-decoration: none; border-radius: 12px;
           padding: 16px; font-weight: 600; margin-bottom: 12px; }
a.primary { background: #2f4fea; color: #fff; }
a.secondary { background: #fff; color: #1c1917; }
</style>
</head>
<body>
<main>
<h1>$heading</h1>
<p>$explanation</p>
${highlight?.let { "<div class=\"code\">$it</div>" } ?: ""}
<a class="button primary" href="$webUrl">Продолжить в браузере</a>
<a class="button secondary" href="$appUrl">Открыть приложение</a>
<a class="button secondary" href="$downloadUrl">Установить приложение</a>
</main>
<script>window.location.href = "$webUrl";</script>
</body>
</html>
""".trimIndent()
