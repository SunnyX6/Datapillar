import React, { useState, useEffect } from 'react';
import { Menu, X, Layers } from 'lucide-react';

interface NavbarProps {
  onRequestAccess: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ onRequestAccess }) => {
  const [isScrolled, setIsScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <nav className={`fixed top-0 left-0 right-0 z-50 transition-all duration-500 border-b ${
      isScrolled 
        ? 'bg-[#020410]/80 backdrop-blur-md border-violet-500/10 py-3' 
        : 'bg-transparent border-transparent py-5'
    }`}>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center">
          <div className="flex items-center space-x-2.5 group cursor-pointer">
            {/* Datapillar Logo - Abstract Pillar/Layers */}
            <div className="w-8 h-8 rounded-lg bg-gradient-to-b from-violet-600 to-cyan-600 flex items-center justify-center shadow-[0_0_15px_rgba(99,102,241,0.5)]">
                <Layers className="text-white w-5 h-5" />
            </div>
            <div className="flex flex-col">
              <span className="text-lg font-bold text-white tracking-tight leading-none">Datapillar</span>
              <span className="text-[10px] text-cyan-400 tracking-widest font-mono">INTELLIGENCE</span>
            </div>
          </div>

          <div className="hidden md:flex items-center space-x-8">
            <a href="#features" className="text-slate-300 hover:text-white transition-colors text-sm font-medium hover:text-shadow-glow">产品功能</a>
            <a href="#testimonials" className="text-slate-300 hover:text-white transition-colors text-sm font-medium hover:text-shadow-glow">解决方案</a>
            <a href="#pricing" className="text-slate-300 hover:text-white transition-colors text-sm font-medium hover:text-shadow-glow">价格方案</a>
            <button 
              onClick={onRequestAccess}
              className="px-5 py-2 rounded-full bg-[#5558ff] hover:bg-[#4548e6] text-white text-xs font-bold transition-all shadow-[0_0_15px_rgba(85,88,255,0.4)]"
            >
              申请试用
            </button>
          </div>

          <div className="md:hidden">
            <button onClick={() => setMobileMenuOpen(!mobileMenuOpen)} className="text-slate-300 hover:text-white">
              {mobileMenuOpen ? <X /> : <Menu />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Menu */}
      {mobileMenuOpen && (
        <div className="md:hidden bg-[#020410] border-t border-slate-800 absolute w-full h-screen">
          <div className="px-4 pt-4 pb-6 space-y-4 flex flex-col">
            <a href="#features" onClick={() => setMobileMenuOpen(false)} className="text-lg text-slate-300 hover:text-white font-medium">产品功能</a>
            <a href="#pricing" onClick={() => setMobileMenuOpen(false)} className="text-lg text-slate-300 hover:text-white font-medium">价格方案</a>
            <button 
              onClick={() => {
                setMobileMenuOpen(false);
                onRequestAccess();
              }}
              className="w-full mt-4 py-3 rounded-lg bg-[#5558ff] text-white font-bold"
            >
              申请试用
            </button>
          </div>
        </div>
      )}
    </nav>
  );
};