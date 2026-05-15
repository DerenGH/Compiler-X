package com.example.compilerx

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class QuizFragment : Fragment() {

    private lateinit var database: DatabaseReference

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_quiz_setup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = FirebaseDatabase.getInstance("https://compiler-x-71cad-default-rtdb.europe-west1.firebasedatabase.app/")
            .getReference("Quizzes")

        val btnStart = view.findViewById<Button>(R.id.btnStartQuiz)
        val rgSubjects = view.findViewById<RadioGroup>(R.id.rgSubjects)
        val sbQuestions = view.findViewById<SeekBar>(R.id.sbQuestions)

        btnStart.setOnClickListener {
            val selectedId = rgSubjects.checkedRadioButtonId
            if (selectedId == -1) {
                Toast.makeText(requireContext(), "Select a subject!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val subject = when(selectedId) {
                R.id.rbPython -> "Python"
                R.id.rbHtml -> "HTML"
                R.id.rbJava -> "Java"
                else -> "Python"
            }

            // Get timer state and count at click time
            val swTimer = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swTimer)
            val isTimerEnabled = swTimer?.isChecked ?: true
            val count = if (sbQuestions.progress < 1) 1 else sbQuestions.progress

            startQuiz(subject, count, isTimerEnabled)
        }
    }

    private fun startQuiz(subject: String, count: Int, timerEnabled: Boolean) {
        database.child(subject).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val allQuestions = mutableListOf<Question>()
                for (child in snapshot.children) {
                    child.getValue(Question::class.java)?.let { allQuestions.add(it) }
                }

                if (allQuestions.isNotEmpty()) {
                    val selectedQuestions = allQuestions.shuffled().take(count)
                    // Pass both parameters to fix the "No value passed" error
                    navigateToPlayScreen(selectedQuestions, timerEnabled)
                }
            }
        }
    }

    private fun navigateToPlayScreen(questions: List<Question>, timerEnabled: Boolean) {
        val playFragment = PlayQuizFragment.newInstance(questions, timerEnabled)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, playFragment)
            .addToBackStack(null)
            .commit()
    }
}