package com.axiim.q.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.axiim.q.data.AxiimRepository
import com.axiim.q.data.AxiimUiState
import kotlinx.coroutines.flow.StateFlow

class AxiimViewModel(application: Application) : AndroidViewModel(application) {
  val repository = AxiimRepository(application.applicationContext)
  val uiState: StateFlow<AxiimUiState> = repository.state

  override fun onCleared() {
    super.onCleared()
    repository.close()
  }
}
