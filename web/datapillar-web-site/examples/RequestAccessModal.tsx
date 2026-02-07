import React, { useState } from 'react';
import { X, Loader2, CheckCircle } from 'lucide-react';
import { ApplicationStatus } from '../types';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const RequestAccessModal: React.FC<ModalProps> = ({ isOpen, onClose }) => {
  const [status, setStatus] = useState<ApplicationStatus>(ApplicationStatus.IDLE);
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    company: '',
    role: 'CTO'
  });

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setStatus(ApplicationStatus.SUBMITTING);
    // Simulate API call
    setTimeout(() => {
      setStatus(ApplicationStatus.SUCCESS);
    }, 1500);
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setFormData(prev => ({ ...prev, [e.target.name]: e.target.value }));
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/80 backdrop-blur-sm" onClick={onClose}></div>

      {/* Modal */}
      <div className="relative w-full max-w-lg bg-slate-900 border border-slate-700 rounded-2xl shadow-2xl overflow-hidden animate-[fadeIn_0.2s_ease-out]">
        <button 
          onClick={onClose}
          className="absolute top-4 right-4 text-slate-400 hover:text-white transition-colors"
        >
          <X className="w-6 h-6" />
        </button>

        <div className="p-8">
          {status === ApplicationStatus.SUCCESS ? (
            <div className="text-center py-10">
              <div className="w-20 h-20 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-6">
                <CheckCircle className="w-10 h-10 text-green-500" />
              </div>
              <h3 className="text-2xl font-bold text-white mb-2">申请已提交！</h3>
              <p className="text-slate-400 mb-8">
                感谢您对 Datapillar 的关注。我们的团队将审核您的申请，并在 24 小时内将邀请码发送至 <strong>{formData.email}</strong>。
              </p>
              <button 
                onClick={onClose}
                className="px-6 py-2 bg-slate-800 hover:bg-slate-700 text-white rounded-lg transition-colors"
              >
                关闭
              </button>
            </div>
          ) : (
            <>
              <h2 className="text-2xl font-bold text-white mb-2">申请早期试用</h2>
              <p className="text-slate-400 mb-8 text-sm">
                加入数百家高增长企业，使用 Datapillar 驱动数据智能。
              </p>

              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label htmlFor="name" className="block text-sm font-medium text-slate-300 mb-1">姓名</label>
                  <input
                    type="text"
                    id="name"
                    name="name"
                    required
                    value={formData.name}
                    onChange={handleChange}
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-4 py-2.5 text-white focus:outline-none focus:border-violet-500 focus:ring-1 focus:ring-violet-500"
                    placeholder="张三"
                  />
                </div>

                <div>
                  <label htmlFor="email" className="block text-sm font-medium text-slate-300 mb-1">工作邮箱</label>
                  <input
                    type="email"
                    id="email"
                    name="email"
                    required
                    value={formData.email}
                    onChange={handleChange}
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-4 py-2.5 text-white focus:outline-none focus:border-violet-500 focus:ring-1 focus:ring-violet-500"
                    placeholder="zhangsan@company.com"
                  />
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label htmlFor="company" className="block text-sm font-medium text-slate-300 mb-1">公司名称</label>
                    <input
                      type="text"
                      id="company"
                      name="company"
                      required
                      value={formData.company}
                      onChange={handleChange}
                      className="w-full bg-slate-950 border border-slate-700 rounded-lg px-4 py-2.5 text-white focus:outline-none focus:border-violet-500 focus:ring-1 focus:ring-violet-500"
                      placeholder="未来科技"
                    />
                  </div>
                  <div>
                    <label htmlFor="role" className="block text-sm font-medium text-slate-300 mb-1">职位</label>
                    <select
                      id="role"
                      name="role"
                      value={formData.role}
                      onChange={handleChange}
                      className="w-full bg-slate-950 border border-slate-700 rounded-lg px-4 py-2.5 text-white focus:outline-none focus:border-violet-500 focus:ring-1 focus:ring-violet-500"
                    >
                      <option value="CTO">CTO / 技术副总裁</option>
                      <option value="DataEngineer">数据工程师</option>
                      <option value="Product">产品经理</option>
                      <option value="Other">其他</option>
                    </select>
                  </div>
                </div>

                <div className="pt-4">
                  <button
                    type="submit"
                    disabled={status === ApplicationStatus.SUBMITTING}
                    className="w-full py-3 bg-[#5558ff] hover:bg-[#4548e6] text-white rounded-lg font-bold shadow-lg shadow-violet-500/20 transition-all flex items-center justify-center gap-2"
                  >
                    {status === ApplicationStatus.SUBMITTING ? (
                      <>
                        <Loader2 className="w-5 h-5 animate-spin" />
                        提交中...
                      </>
                    ) : (
                      '提交申请'
                    )}
                  </button>
                  <p className="text-center text-xs text-slate-500 mt-4">
                    提交即代表您同意我们的服务条款和隐私政策。
                  </p>
                </div>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
