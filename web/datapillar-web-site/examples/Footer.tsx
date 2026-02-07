import React from 'react';
import { Layers, Twitter, Github, Linkedin } from 'lucide-react';

export const Footer: React.FC = () => {
  return (
    <footer className="bg-slate-950 border-t border-slate-800 pt-16 pb-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-12 mb-12">
          <div className="col-span-1 md:col-span-1">
            <div className="flex items-center space-x-2 mb-4">
               <div className="w-8 h-8 bg-gradient-to-br from-violet-600 to-cyan-600 rounded-lg flex items-center justify-center">
                <Layers className="text-white w-5 h-5" />
              </div>
              <span className="text-xl font-bold text-white tracking-tight">Datapillar</span>
            </div>
            <p className="text-slate-400 text-sm leading-relaxed">
              赋予数据团队构建未来智能的能力。安全、可扩展，前所未有的简单。
            </p>
          </div>
          
          <div>
            <h4 className="text-white font-semibold mb-4">产品</h4>
            <ul className="space-y-2 text-sm text-slate-400">
              <li><a href="#" className="hover:text-violet-400 transition-colors">核心功能</a></li>
              <li><a href="#" className="hover:text-violet-400 transition-colors">集成能力</a></li>
              <li><a href="#" className="hover:text-violet-400 transition-colors">企业版</a></li>
              <li><a href="#" className="hover:text-violet-400 transition-colors">更新日志</a></li>
            </ul>
          </div>

          <div>
            <h4 className="text-white font-semibold mb-4">公司</h4>
            <ul className="space-y-2 text-sm text-slate-400">
              <li><a href="#" className="hover:text-violet-400 transition-colors">关于我们</a></li>
              <li><a href="#" className="hover:text-violet-400 transition-colors">加入我们</a></li>
              <li><a href="#" className="hover:text-violet-400 transition-colors">技术博客</a></li>
              <li><a href="#" className="hover:text-violet-400 transition-colors">法律条款</a></li>
            </ul>
          </div>

          <div>
            <h4 className="text-white font-semibold mb-4">关注我们</h4>
            <div className="flex space-x-4">
              <a href="#" className="text-slate-400 hover:text-white transition-colors">
                <Twitter className="w-5 h-5" />
              </a>
              <a href="#" className="text-slate-400 hover:text-white transition-colors">
                <Github className="w-5 h-5" />
              </a>
              <a href="#" className="text-slate-400 hover:text-white transition-colors">
                <Linkedin className="w-5 h-5" />
              </a>
            </div>
          </div>
        </div>

        <div className="border-t border-slate-800 pt-8 flex flex-col md:flex-row justify-between items-center gap-4">
          <p className="text-slate-500 text-sm">
            © {new Date().getFullYear()} Datapillar Inc. 保留所有权利.
          </p>
          <div className="flex items-center gap-2 text-xs text-slate-600">
             <span className="w-2 h-2 rounded-full bg-green-500"></span>
             <span>所有系统运行正常</span>
          </div>
        </div>
      </div>
    </footer>
  );
};
