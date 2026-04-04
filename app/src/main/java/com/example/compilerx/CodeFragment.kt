package com.example.compilerx

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.*
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.*

class CodeFragment : Fragment(R.layout.fragment_code) {

    private val projectFiles = HashMap<String, String>()
    private var activeFileName: String? = null
    private val consoleLogs = mutableListOf<String>()

    // Real-time stream variables
    private var terminalOutStream = PipedOutputStream()
    private var pythonStdin: PipedInputStream? = null

    private val CREATE_FILE = 1
    private val PICK_FILE = 2
    private var isApplyingHighlights = false

    private lateinit var codeEditor: EditText
    private lateinit var btnRun: Button
    private lateinit var btnMenu: ImageButton
    private lateinit var drawerLayout: DrawerLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(requireContext()))
        }

        drawerLayout = view.findViewById(R.id.drawerLayout)
        btnMenu = view.findViewById(R.id.btnMenu)
        val tabsLayout: LinearLayout = view.findViewById(R.id.tabsLayout)
        val helperBar: LinearLayout = view.findViewById(R.id.helperBar)

        btnRun = view.findViewById(R.id.btnRun)
        codeEditor = view.findViewById(R.id.codeEditor)

        val menuNewProject: Button = view.findViewById(R.id.menuNewProject)
        val menuOpenFile: Button = view.findViewById(R.id.menuOpenFile)
        val menuSaveFile: Button = view.findViewById(R.id.menuSaveFile)

        codeEditor.setHorizontallyScrolling(true)

        codeEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s == null || isApplyingHighlights) return
                isApplyingHighlights = true
                val ext = activeFileName?.substringAfterLast(".", "") ?: ""
                try {
                    when (ext) {
                        "py" -> applyPythonHighlighting(s)
                        "java" -> applyJavaHighlighting(s)
                        "html", "css", "js" -> applyWebHighlighting(s)
                    }
                } finally {
                    isApplyingHighlights = false
                }
            }
        })

        val shortcuts = arrayOf(":", "tab", "( )", "[ ]", "{ }", "\"", "'", "print(", "def ", "public ", "static ")
        shortcuts.forEach { text ->
            helperBar.addView(createHelperButton(text, codeEditor))
        }

        btnMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        menuNewProject.setOnClickListener {
            showNewProjectDialog(codeEditor, tabsLayout)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        menuOpenFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, PICK_FILE)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        menuSaveFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, activeFileName ?: "code.txt")
            }
            startActivityForResult(intent, CREATE_FILE)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        btnRun.setOnClickListener {
            handleRunSequence()
        }
    }

    override fun onResume() {
        super.onResume()
        applySettings()
    }

    private fun applySettings() {
        val prefs = requireActivity().getSharedPreferences("CompilerX_Prefs", Context.MODE_PRIVATE)
        val root = requireView()

        val themeIndex = prefs.getInt("theme_index", 0)
        val (bgColor, textColor, editorBg, accentColor) = when (themeIndex) {
            0 -> arrayOf("#000000", "#FFFFFF", "#121212", "#4CAF50")
            1 -> arrayOf("#F0F0F0", "#000000", "#FFFFFF", "#2196F3")
            2 -> arrayOf("#0D1117", "#9CDCFE", "#161B22", "#58A6FF")
            3 -> arrayOf("#1B2B34", "#6699CC", "#233139", "#6699CC")
            else -> arrayOf("#000000", "#FFFFFF", "#121212", "#4CAF50")
        }

        val bgInt = Color.parseColor(bgColor)
        val textInt = Color.parseColor(textColor)
        val editorInt = Color.parseColor(editorBg)
        val accentInt = Color.parseColor(accentColor)

        // Fix for Tablet Status Bar and Navigation Bar
        val window = requireActivity().window
        window.statusBarColor = bgInt
        window.navigationBarColor = bgInt
        if (themeIndex == 1) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            window.decorView.systemUiVisibility = 0
        }

        val sizes = arrayOf(12f, 14f, 16f, 18f, 22f)
        val sizeIndex = prefs.getInt("font_size_index", 1)
        codeEditor.textSize = sizes[sizeIndex]

        root.setBackgroundColor(bgInt)

        val btnMenuShape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(editorInt)
        }
        btnMenu.background = btnMenuShape
        btnMenu.imageTintList = ColorStateList.valueOf(accentInt)

        val settingsCards = listOf(R.id.profileCard, R.id.visualEngineCard, R.id.executionCard)
        settingsCards.forEach { id ->
            root.findViewById<com.google.android.material.card.MaterialCardView>(id)?.let { card ->
                card.setCardBackgroundColor(editorInt)
                card.radius = 30f
                card.strokeColor = Color.parseColor("#33FFFFFF")
                card.strokeWidth = 2
            }
        }

        val headers = listOf(R.id.labelVisualEngineHeader, R.id.labelExecutionEngineHeader, R.id.settings_title)
        headers.forEach { id -> root.findViewById<TextView>(id)?.setTextColor(accentInt) }

        val textElements = listOf(R.id.tvSettingsUsername, R.id.labelTheme, R.id.labelFontSize, R.id.labelAutoSave, R.id.labelClearConsole)
        textElements.forEach { id -> root.findViewById<TextView>(id)?.setTextColor(textInt) }

        root.findViewById<Button>(R.id.btnLogOut)?.setTextColor(Color.WHITE)
        val switches = listOf(R.id.switchAutoSave, R.id.switchClearConsole)
        switches.forEach { id ->
            root.findViewById<androidx.appcompat.widget.SwitchCompat>(id)?.let { sw ->
                sw.thumbTintList = ColorStateList.valueOf(accentInt)
                sw.trackTintList = ColorStateList.valueOf(accentInt).withAlpha(128)
            }
        }

        val navView = root.findViewById<View>(R.id.nav_view)
        navView?.setBackgroundColor(bgInt)
        root.findViewById<TextView>(R.id.menuTitle)?.setTextColor(accentInt)

        val drawerButtons = listOf(R.id.menuNewProject, R.id.menuOpenFile, R.id.menuSaveFile, R.id.menuShare)
        drawerButtons.forEach { id ->
            root.findViewById<Button>(id)?.let { btn ->
                btn.backgroundTintList = ColorStateList.valueOf(editorInt)
                btn.setTextColor(textInt)
            }
        }

        val bottomNav = requireActivity().findViewById<View>(R.id.bottom_navigation)
        bottomNav?.let { nav ->
            nav.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(45f, 45f, 45f, 45f, 0f, 0f, 0f, 0f)
                setColor(bgInt)
                setStroke(2, Color.parseColor("#33FFFFFF"))
            }
        }

        codeEditor.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 30f
            setColor(editorInt)
        }
        codeEditor.setTextColor(textInt)

        val helperBar = root.findViewById<LinearLayout>(R.id.helperBar)
        helperBar?.setBackgroundColor(bgInt)

        for (i in 0 until (helperBar?.childCount ?: 0)) {
            val child = helperBar?.getChildAt(i)
            if (child is Button) {
                child.background = GradientDrawable().apply {
                    cornerRadius = 15f
                    setColor(editorInt)
                    setStroke(2, accentInt)
                }
                child.setTextColor(textInt)
            }
        }
        btnRun.backgroundTintList = ColorStateList.valueOf(accentInt)
    }

    private fun applyThemeToDialog(dialogView: View) {
        val prefs = requireActivity().getSharedPreferences("CompilerX_Prefs", Context.MODE_PRIVATE)
        val themeIndex = prefs.getInt("theme_index", 0)
        val (bgColor, textColor, cardBg, accentColor) = when (themeIndex) {
            1 -> arrayOf("#F0F0F0", "#000000", "#FFFFFF", "#2196F3")
            else -> arrayOf("#000000", "#FFFFFF", "#121212", "#4CAF50")
        }
        val textInt = Color.parseColor(textColor)
        val cardInt = Color.parseColor(cardBg)
        val accentInt = Color.parseColor(accentColor)

        val dialogCard = dialogView.findViewById<View>(R.id.dialogCard) ?: dialogView
        dialogCard.background = GradientDrawable().apply {
            cornerRadius = 30f
            setColor(cardInt)
            setStroke(2, Color.parseColor("#33FFFFFF"))
        }

        dialogView.findViewById<TextView>(R.id.textView2)?.setTextColor(textInt)

        dialogView.findViewById<EditText>(R.id.etProjectName)?.let {
            it.setTextColor(textInt)
            it.setHintTextColor(Color.parseColor("#80AAAADB"))
            it.backgroundTintList = ColorStateList.valueOf(accentInt)
        }

        dialogView.findViewById<Button>(R.id.btnCreate)?.let {
            it.backgroundTintList = ColorStateList.valueOf(accentInt)
            it.setTextColor(if (themeIndex == 1) Color.WHITE else Color.BLACK)
        }
    }

    private fun handleRunSequence() {
        val prefs = requireActivity().getSharedPreferences("CompilerX_Prefs", Context.MODE_PRIVATE)
        activeFileName?.let { projectFiles[it] = codeEditor.text.toString() }

        if (prefs.getBoolean("clear_console", true)) consoleLogs.clear()

        val code = codeEditor.text.toString()
        val ext = activeFileName?.substringAfterLast(".", "") ?: ""

        when (ext) {
            "html", "css", "js" -> showWebPreview()
            "py" -> {
                showConsole()
                runFullPython(code)
            }
            "java" -> {
                showConsole()
                runFullJava(code)
            }
            else -> Toast.makeText(requireContext(), "Create/Select a file first", Toast.LENGTH_SHORT).show()
        }
    }

    private var currentConsoleDialog: Dialog? = null
    private fun showConsole() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_console)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        currentConsoleDialog = dialog

        val etInput = dialog.findViewById<EditText>(R.id.etTerminalInput)
        val btnSend = dialog.findViewById<ImageButton>(R.id.btnSendInput)

        // Reset the real-time stream for this run
        terminalOutStream = PipedOutputStream()
        pythonStdin = PipedInputStream(terminalOutStream)

        btnSend.setOnClickListener {
            val text = etInput.text.toString()
            if (text.isNotEmpty()) {
                consoleLogs.add("> $text")
                updateConsoleUI()
                Thread {
                    try {
                        terminalOutStream.write((text + "\n").toByteArray())
                        terminalOutStream.flush()
                    } catch (e: Exception) {}
                }.start()
                etInput.setText("")
            }
        }

        dialog.findViewById<Button>(R.id.btnExitConsole).setOnClickListener { dialog.dismiss() }
        updateConsoleUI()
        dialog.show()
    }

    private fun runFullPython(code: String) {
        Thread {
            try {
                val py = Python.getInstance()
                val sys = py.getModule("sys")
                val io = py.getModule("io")

                val outputStream = io.callAttr("StringIO")
                sys.put("stdout", outputStream)
                sys.put("stderr", outputStream)

                // Attach the real-time pipe. Python input() will wait for the pipe.
                sys.put("stdin", pythonStdin)

                val mainModule = py.getModule("__main__")

                // UI Poller to show text WHILE code is running
                val handler = Handler(Looper.getMainLooper())
                val poller = object : Runnable {
                    override fun run() {
                        val currentOut = outputStream.callAttr("getvalue").toString()
                        if (currentOut.isNotEmpty()) {
                            // Only add if there is fresh data
                            val outLines = currentOut.trimEnd().split("\n")
                            if (outLines.size > (consoleLogs.filter { !it.startsWith(">") }.size)) {
                                consoleLogs.add(outLines.last())
                                updateConsoleUI()
                            }
                        }
                        if (currentConsoleDialog?.isShowing == true) handler.postDelayed(this, 300)
                    }
                }
                handler.post(poller)

                py.getBuiltins()?.get("exec")?.call(code, mainModule?.get("__dict__"))

                val finalResult = outputStream.callAttr("getvalue").toString()
                handler.removeCallbacks(poller)
                activity?.runOnUiThread {
                    if (finalResult.isNotEmpty()) {
                        consoleLogs.add("> Process finished.")
                    } else {
                        consoleLogs.add("> Process finished.")
                    }
                    updateConsoleUI()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    consoleLogs.add("> ERROR: ${e.message}")
                    updateConsoleUI()
                }
            }
        }.start()
    }

    private fun runFullJava(code: String) {
        Thread {
            val outputStream = ByteArrayOutputStream()
            val printStream = PrintStream(outputStream)
            val oldOut = System.out; val oldErr = System.err; val oldIn = System.`in`

            System.setIn(pythonStdin) // Reuse the same pipe for Java
            System.setOut(printStream); System.setErr(printStream)

            try {
                val interpreter = bsh.Interpreter()
                interpreter.eval(code)
                val result = outputStream.toString()
                activity?.runOnUiThread {
                    consoleLogs.add(result.ifEmpty { "> Process finished." })
                    updateConsoleUI()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    consoleLogs.add("> ERROR: ${e.message}")
                    updateConsoleUI()
                }
            } finally {
                System.setOut(oldOut); System.setErr(oldErr); System.setIn(oldIn)
            }
        }.start()
    }

    private fun updateConsoleUI() {
        activity?.runOnUiThread {
            if (currentConsoleDialog?.isShowing == true) {
                val container = currentConsoleDialog?.findViewById<LinearLayout>(R.id.consoleLogContainer)
                container?.removeAllViews()
                for (log in consoleLogs) {
                    container?.addView(TextView(requireContext()).apply {
                        text = log
                        setTextColor(Color.GREEN)
                        setPadding(10, 5, 10, 5)
                        textSize = 14f
                        typeface = Typeface.MONOSPACE
                    })
                }
                val scrollView = currentConsoleDialog?.findViewById<ScrollView>(R.id.consoleScroll)
                scrollView?.post { scrollView.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private fun applyPythonHighlighting(editable: Editable) {
        clearSpans(editable)
        val patterns = mapOf(
            "\\b(def|class|if|else|elif|while|for|return|import|from|as|try|except|print|in|is|not|with|pass|None|True|False)\\b".toRegex() to "#FF7B00",
            "(\".*?\"|'.*?')".toRegex() to "#6A9955",
            "(#.*)".toRegex() to "#808080",
            "\\b(\\w+)(?=\\s*\\()".toRegex() to "#4EC9B0",
            "\\b(\\d+)\\b".toRegex() to "#C586C0"
        )
        applyPatterns(editable, patterns)
    }

    private fun applyJavaHighlighting(editable: Editable) {
        clearSpans(editable)
        val patterns = mapOf(
            "\\b(public|private|protected|class|static|void|int|double|float|long|String|boolean|if|else|for|while|return|new|import|package|try|catch|finally)\\b".toRegex() to "#569CD6",
            "(\".*?\"|'.*?')".toRegex() to "#CE9178",
            "(//.*|/\\*.*?\\*/)".toRegex() to "#6A9955",
            "\\b(\\w+)(?=\\s*\\()".toRegex() to "#DCDCAA",
            "\\b(\\d+)\\b".toRegex() to "#B5CEA8"
        )
        applyPatterns(editable, patterns)
    }

    private fun applyWebHighlighting(editable: Editable) {
        clearSpans(editable)
        val standardBlue = Color.parseColor("#569CD6")
        val lightBlue = Color.parseColor("#4EC9B0")
        val lightGray = Color.parseColor("#A9A9A9")
        val orange = Color.parseColor("#CE9178")
        "(<\\/?|\\/?>|<!|>)".toRegex().findAll(editable).forEach { match ->
            editable.setSpan(ForegroundColorSpan(lightGray), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        "(?<=<|\\/|<!)([a-zA-Z1-6]+)".toRegex().findAll(editable).forEach { match ->
            editable.setSpan(ForegroundColorSpan(standardBlue), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        "\\b(rel|type|href|src|id|class|html|body|head|script|link)\\b".toRegex().findAll(editable).forEach { match ->
            editable.setSpan(ForegroundColorSpan(lightBlue), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        "(\".*?\"|'.*?')".toRegex().findAll(editable).forEach { match ->
            editable.setSpan(ForegroundColorSpan(orange), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun clearSpans(editable: Editable) {
        val spans = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
        for (span in spans) editable.removeSpan(span)
    }

    private fun applyPatterns(editable: Editable, patterns: Map<Regex, String>) {
        for ((regex, colorStr) in patterns) {
            regex.findAll(editable).forEach { match ->
                editable.setSpan(ForegroundColorSpan(Color.parseColor(colorStr)), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun showWebPreview() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }
        val topMenu = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val btnClose = Button(requireContext()).apply {
            text = "CLOSE PREVIEW"; setTextColor(Color.RED); setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { dialog.dismiss() }
        }
        topMenu.addView(btnClose)
        root.addView(topMenu)
        val webView = WebView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            settings.javaScriptEnabled = true
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                    message?.let { consoleLogs.add("[JS] ${it.message()}"); updateConsoleUI() }
                    return true
                }
            }
            val finalData = "<html><head><style>${projectFiles["style.css"] ?: ""}</style></head><body>${projectFiles["index.html"] ?: ""}<script>${projectFiles["script.js"] ?: ""}</script></body></html>"
            loadDataWithBaseURL(null, finalData, "text/html", "UTF-8", null)
        }
        root.addView(webView)
        dialog.setContentView(root)
        dialog.show()
    }

    private fun addFileToProject(fileName: String, content: String, tabsContainer: LinearLayout, editor: EditText) {
        projectFiles[fileName] = content
        val tab = TextView(requireContext()).apply {
            text = fileName; setPadding(40, 20, 40, 20); setTextColor(Color.LTGRAY)
            typeface = Typeface.MONOSPACE
        }
        tab.setOnClickListener {
            activeFileName?.let { projectFiles[it] = editor.text.toString() }
            for (i in 0 until tabsContainer.childCount) (tabsContainer.getChildAt(i) as TextView).setTextColor(Color.LTGRAY)
            tab.setTextColor(Color.WHITE); activeFileName = fileName; editor.setText(projectFiles[fileName])
        }
        tabsContainer.addView(tab)
        if (tabsContainer.childCount == 1) tab.performClick()
    }

    private fun createHelperButton(txt: String, editor: EditText) = Button(requireContext()).apply {
        text = if(txt == "tab") "TAB" else txt
        setTextColor(Color.WHITE); setAllCaps(false)
        background = GradientDrawable().apply { cornerRadius = 15f; setColor(Color.parseColor("#333333")) }
        layoutParams = LinearLayout.LayoutParams(-2, 100).apply { setMargins(8, 0, 8, 0) }
        setOnClickListener {
            val insert = if (txt == "tab") "    " else txt
            editor.text.replace(editor.selectionStart, editor.selectionEnd, insert)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        val contentResolver = requireContext().contentResolver
        when (requestCode) {
            CREATE_FILE -> try {
                contentResolver.openOutputStream(uri)?.use { it.write(codeEditor.text.toString().toByteArray()) }
                Toast.makeText(requireContext(), "Saved Successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {}
            PICK_FILE -> try {
                val content = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val fileName = uri.path?.substringAfterLast("/") ?: "imported_file"
                addFileToProject(fileName, content, requireView().findViewById(R.id.tabsLayout), codeEditor)
            } catch (e: Exception) {}
        }
    }

    private fun showNewProjectDialog(editor: EditText, tabs: LinearLayout) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_new_project)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        applyThemeToDialog(dialog.findViewById(android.R.id.content))

        val etName = dialog.findViewById<EditText>(R.id.etProjectName)
        val spinner = dialog.findViewById<Spinner>(R.id.dialogLanguageSpinner)
        val btnCreate = dialog.findViewById<Button>(R.id.btnCreate)
        val htmlOptionsLayout = dialog.findViewById<LinearLayout>(R.id.htmlOptionsLayout)
        val rbCssJs = dialog.findViewById<RadioButton>(R.id.rbCssJs)

        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayOf("Python", "HTML", "Java"))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                htmlOptionsLayout.visibility = if (spinner.selectedItem == "HTML") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        btnCreate.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) return@setOnClickListener
            projectFiles.clear(); tabs.removeAllViews()
            when(spinner.selectedItem.toString()) {
                "HTML" -> {
                    addFileToProject("index.html", "<!DOCTYPE html>\n<html>\n<body>\n<h1>Hello!</h1>\n</body>\n</html>", tabs, editor)
                    if (rbCssJs.isChecked) {
                        addFileToProject("style.css", "body { background: #121212; color: #4CAF50; }", tabs, editor)
                        addFileToProject("script.js", "console.log('Online');", tabs, editor)
                    }
                }
                "Python" -> addFileToProject("$name.py", "print(\"Hello Python!\")", tabs, editor)
                "Java" -> addFileToProject("$name.java", "System.out.println(\"Hello Java!\");", tabs, editor)
            }
            dialog.dismiss()
        }
        dialog.show()
    }
}