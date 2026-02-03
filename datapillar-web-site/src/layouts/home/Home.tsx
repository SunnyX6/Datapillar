import { useState } from 'react'
import { ChatWidget, Features, Footer, Hero, Navbar, Pricing, RequestAccessModal } from './sections'

export function HomeLayout() {
  const [isRequestOpen, setIsRequestOpen] = useState(false)

  const handleRequestAccess = () => setIsRequestOpen(true)
  const handleCloseModal = () => setIsRequestOpen(false)

  return (
    <div className="min-h-dvh w-full bg-[#020410] text-white overflow-y-auto overflow-x-hidden custom-scrollbar">
      <Navbar onRequestAccess={handleRequestAccess} />
      <main>
        <Hero onRequestAccess={handleRequestAccess} />
        <Features />
        <Pricing onRequestAccess={handleRequestAccess} />
        <Footer />
      </main>
      <RequestAccessModal isOpen={isRequestOpen} onClose={handleCloseModal} />
      <ChatWidget />
    </div>
  )
}
