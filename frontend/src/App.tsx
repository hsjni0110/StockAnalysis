import { BrowserRouter as Router, Routes, Route } from 'react-router-dom'
import { Dashboard } from './components/Dashboard'
import { DeltaMapPage } from './pages/DeltaMapPage'

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/deltamap/:filingId" element={<DeltaMapPage />} />
      </Routes>
    </Router>
  )
}

export default App
