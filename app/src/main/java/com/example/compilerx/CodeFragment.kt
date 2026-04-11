package com.example.compilerx

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CodeViewModel : ViewModel() {
    val projectFiles = HashMap<String, String>()
    val fileUris = HashMap<String, Uri>()
    var activeFileName: String? = null
}

class CodeFragment : Fragment(R.layout.fragment_code) {

    // These now reference the ViewModel instead of local memory
    private lateinit var viewModel: CodeViewModel

    private val consoleLogs = mutableListOf<String>()
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

        // Initialize ViewModel first
        viewModel = ViewModelProvider(requireActivity()).get(CodeViewModel::class.java)

        requireActivity().requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

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

        // --- STATE RESTORATION FROM VIEWMODEL ---
        if (viewModel.projectFiles.isNotEmpty()) {
            tabsLayout.removeAllViews()
            // We use a temporary list to avoid ConcurrentModificationException while re-adding
            val currentFiles = HashMap(viewModel.projectFiles)
            currentFiles.forEach { (name, content) ->
                // This adds the tab back to the UI
                addFileToProject(name, content, tabsLayout, codeEditor)
            }

            // Restore the editor text and active tab highlight
            viewModel.activeFileName?.let { name ->
                codeEditor.setText(viewModel.projectFiles[name])
                forceSelectTab(name)
            }
        }

        codeEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s == null) return
                // Update ViewModel immediately
                viewModel.activeFileName?.let { viewModel.projectFiles[it] = s.toString() }

                if (isApplyingHighlights) return
                isApplyingHighlights = true
                val ext = viewModel.activeFileName?.substringAfterLast(".", "") ?: ""
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
            if (viewModel.activeFileName == null) {
                Toast.makeText(requireContext(), "Create a file first", Toast.LENGTH_SHORT).show()
                drawerLayout.closeDrawer(GravityCompat.START)
                return@setOnClickListener
            }

            val extension = viewModel.activeFileName?.substringAfterLast(".", "txt") ?: "txt"
            val mimeType = when (extension) {
                "py" -> "text/x-python"
                "java" -> "text/x-java-source"
                "html" -> "text/html"
                "css" -> "text/css"
                "js" -> "application/javascript"
                else -> "text/plain"
            }

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, viewModel.activeFileName)
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

        val window = requireActivity().window
        window.statusBarColor = bgInt
        window.navigationBarColor = bgInt
        window.decorView.systemUiVisibility = if (themeIndex == 1) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0

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

        codeEditor.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 30f
            setColor(editorInt)
        }
        codeEditor.setTextColor(textInt)

        val hBar = root.findViewById<LinearLayout>(R.id.helperBar)
        hBar?.setBackgroundColor(bgInt)

        for (i in 0 until (hBar?.childCount ?: 0)) {
            val child = hBar?.getChildAt(i)
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
        viewModel.activeFileName?.let { name ->
            val code = codeEditor.text.toString()
            viewModel.projectFiles[name] = code
            viewModel.fileUris[name]?.let { uri ->
                try {
                    requireContext().contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(code.toByteArray())
                    }
                } catch (e: Exception) {}
            }
        }

        if (prefs.getBoolean("clear_console", true)) consoleLogs.clear()

        val code = codeEditor.text.toString()
        val ext = viewModel.activeFileName?.substringAfterLast(".", "") ?: ""

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

                val bridgeCode = """
import io
import time
import sys

class ConsoleInputStream(io.RawIOBase):
    def __init__(self, java_stream):
        self.stream = java_stream
    def readable(self):
        return True
    def readinto(self, b):
        try:
            while True:
                available = self.stream.available()
                if available > 0:
                    to_read = min(len(b), available)
                    temp = bytearray(to_read)
                    n = self.stream.read(temp)
                    b[:n] = temp[:n]
                    return n
                else:
                    time.sleep(0.05)
        except:
            return 0

def setup_stdin(java_stream):
    raw_io = ConsoleInputStream(java_stream)
    sys.stdin = io.TextIOWrapper(io.BufferedReader(raw_io), line_buffering=True)
            """.trimIndent()

                val mainModule = py.getModule("__main__")
                py.getBuiltins()?.get("exec")?.call(bridgeCode, mainModule?.get("__dict__"))
                mainModule?.get("setup_stdin")?.call(pythonStdin)

                var lastIndex = 0
                val handler = Handler(Looper.getMainLooper())
                val poller = object : Runnable {
                    override fun run() {
                        val fullOutput = outputStream.callAttr("getvalue").toString()
                        if (fullOutput.length > lastIndex) {
                            val newText = fullOutput.substring(lastIndex)
                            newText.split("\n").forEach { line ->
                                if (line.isNotEmpty()) {
                                    activity?.runOnUiThread {
                                        consoleLogs.add(line)
                                        updateConsoleUI()
                                    }
                                }
                            }
                            lastIndex = fullOutput.length
                        }
                        if (currentConsoleDialog?.isShowing == true) {
                            handler.postDelayed(this, 100)
                        }
                    }
                }
                handler.post(poller)
                py.getBuiltins()?.get("exec")?.call(code, mainModule?.get("__dict__"))
                activity?.runOnUiThread {
                    consoleLogs.add("> Process finished.")
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
            System.setIn(pythonStdin)
            System.setOut(printStream); System.setErr(printStream)
            try {
                val interpreter = bsh.Interpreter()
                interpreter.eval(code)
                val result = outputStream.toString()
                activity?.runOnUiThread {
                    result.split("\n").filter { it.isNotEmpty() }.forEach { consoleLogs.add(it) }
                    consoleLogs.add("> Process finished.")
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
        val ctx = context ?: return
        activity?.runOnUiThread {
            if (currentConsoleDialog?.isShowing == true) {
                val container = currentConsoleDialog?.findViewById<LinearLayout>(R.id.consoleLogContainer)
                container?.removeAllViews()
                for (log in consoleLogs) {
                    container?.addView(TextView(ctx).apply {
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
            val finalData = "<html><head><style>${viewModel.projectFiles["style.css"] ?: ""}</style></head><body>${viewModel.projectFiles["index.html"] ?: ""}<script>${viewModel.projectFiles["script.js"] ?: ""}</script></body></html>"
            loadDataWithBaseURL(null, finalData, "text/html", "UTF-8", null)
        }
        root.addView(webView)
        dialog.setContentView(root)
        dialog.show()
    }

    private fun addFileToProject(fileName: String, content: String, tabsContainer: LinearLayout, editor: EditText) {
        viewModel.projectFiles[fileName] = content
        for (i in 0 until tabsContainer.childCount) {
            if ((tabsContainer.getChildAt(i) as TextView).text == fileName) return
        }
        val tab = TextView(requireContext()).apply {
            text = fileName; setPadding(40, 20, 40, 20); setTextColor(Color.LTGRAY)
            typeface = Typeface.MONOSPACE
        }
        tab.setOnClickListener {
            viewModel.activeFileName?.let { viewModel.projectFiles[it] = editor.text.toString() }
            for (i in 0 until tabsContainer.childCount) (tabsContainer.getChildAt(i) as TextView).setTextColor(Color.LTGRAY)
            tab.setTextColor(Color.WHITE)
            viewModel.activeFileName = fileName
            editor.setText(viewModel.projectFiles[fileName])
        }
        tabsContainer.addView(tab)
        if (tabsContainer.childCount == 1 && viewModel.activeFileName == null) tab.performClick()
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

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result ?: "file"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        val contentResolver = requireContext().contentResolver

        when (requestCode) {
            CREATE_FILE -> try {
                contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(codeEditor.text.toString().toByteArray())
                }
                viewModel.activeFileName?.let { viewModel.fileUris[it] = uri }
                Toast.makeText(requireContext(), "Saved Successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Save failed", Toast.LENGTH_SHORT).show()
            }

            PICK_FILE -> try {
                val content = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val fileName = getFileName(uri)
                viewModel.fileUris[fileName] = uri
                addFileToProject(fileName, content, requireView().findViewById(R.id.tabsLayout), codeEditor)
                codeEditor.setText(content)
                forceSelectTab(fileName)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Open failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun forceSelectTab(fileName: String) {
        val tabsLayout: LinearLayout = view?.findViewById(R.id.tabsLayout) ?: return
        for (i in 0 until tabsLayout.childCount) {
            val tab = tabsLayout.getChildAt(i) as TextView
            if (tab.text == fileName) {
                tab.performClick()
                break
            }
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

            // Update ViewModel
            viewModel.projectFiles.clear()
            viewModel.fileUris.clear()
            viewModel.activeFileName = null

            // Clear UI
            tabs.removeAllViews()

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