package com.omr.scanner.student.ui.classdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.omr.scanner.student.databinding.FragmentClassDetailStudentBinding
import com.omr.scanner.student.model.response.MobileClassResponse
import com.omr.scanner.student.ui.applySystemWindowInsets
import com.omr.scanner.student.ui.collectOnStarted
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ClassDetailFragment : Fragment() {
    private var _binding: FragmentClassDetailStudentBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ClassDetailViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClassDetailStudentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        collectOnStarted {
            launch { viewModel.classDetail.collect { it?.let(::bindClass) } }
            launch { viewModel.isLoading.collect { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE } }
            launch { viewModel.message.collect { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() } }
        }
        viewModel.load(arguments?.getString("classId").orEmpty())
    }

    private fun bindClass(item: MobileClassResponse) {
        val info = item.classInfo
        binding.tvClassName.text = info?.className ?: item.className
        binding.tvSubject.text = info?.subject ?: item.subject.orEmpty()
        binding.tvDescription.text = info?.description ?: item.description.orEmpty()
        binding.tvGrade.text = info?.grade ?: item.grade
        binding.tvSchoolYear.text = info?.schoolYear ?: item.schoolYear
        binding.tvStatus.text = item.status ?: info?.status.orEmpty()
        binding.tvJoinCode.text = item.joinCode ?: item.internalId.orEmpty()
        binding.tvStudentCount.text = item.studentCount.toString()
        binding.tvExamCount.text = (item.examCount ?: item.count?.exams ?: item.count?.examAssignments ?: 0).toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
