import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useApp } from '../context/AppContext';
import { ChevronRight, ChevronLeft, Info } from 'lucide-react';

interface TourStep {
    targetId: string;
    title: string;
    content: string;
    tab?: 'phone' | 'chat' | 'settings';
    position: 'top' | 'bottom' | 'center';
}

const steps: TourStep[] = [
    {
        targetId: 'tutorial-welcome',
        title: 'SmartBellへようこそ！',
        content: 'このガイドではアプリの基本的な使い方を説明します。デバイスをベルとして使う準備をしましょう。',
        position: 'center'
    },
    {
        targetId: 'nav-settings',
        title: 'まずは設定',
        content: '最初にMQTTブローカー（サーバー）の設定が必要です。設定タブを開いてみましょう。',
        tab: 'settings',
        position: 'top'
    },
    {
        targetId: 'settings-mqtt-card',
        title: '通信設定',
        content: 'ここにご自身のMQTTサーバーのIPアドレスとポートを入力します。家庭内であればPCのIPアドレスなどが使われます。',
        tab: 'settings',
        position: 'bottom'
    },
    {
        targetId: 'settings-battery-card',
        title: '安定動作の重要設定',
        content: '「バッテリー最適化」を解除することで、画面がオフでも確実に着信できるようになります。必ず「制限なし」に設定してください。',
        tab: 'settings',
        position: 'bottom'
    },
    {
        targetId: 'nav-phone',
        title: '呼び出し機能',
        content: 'メインの呼び出し画面に戻りましょう。',
        tab: 'phone',
        position: 'top'
    },
    {
        targetId: 'sender-bell-button',
        title: 'ベルを鳴らす',
        content: 'このボタンをタップするだけで、登録した相手（または全員）に呼び出しを送信できます。',
        tab: 'phone',
        position: 'top'
    },
    {
        targetId: 'nav-chat',
        title: 'チャット機能',
        content: 'チャット機能では短いメッセージのやり取りができます。',
        tab: 'chat',
        position: 'top'
    },
    {
        targetId: 'tutorial-finish',
        title: '準備完了！',
        content: '以上で主要な機能の説明は終わりです。家族や仲間との通信を楽しみましょう！',
        position: 'center'
    }
];

export const GuideTour: React.FC<{ onTabChange: (tab: 'phone' | 'chat' | 'settings') => void }> = ({ onTabChange }) => {
    const { setShowTutorial, setHasSeenTutorial } = useApp();
    const [currentStep, setCurrentStep] = useState(0);
    const [targetRect, setTargetRect] = useState<DOMRect | null>(null);

    const step = steps[currentStep];

    useEffect(() => {
        if (step.tab) {
            onTabChange(step.tab);
        }

        // Give time for tab rendering
        const timer = setTimeout(() => {
            const el = document.getElementById(step.targetId);
            if (el) {
                setTargetRect(el.getBoundingClientRect());
            } else {
                setTargetRect(null);
            }
        }, 300); // Increased delay for smoother tab transitions

        return () => clearTimeout(timer);
    }, [currentStep, step.targetId, step.tab, onTabChange]);

    const handleNext = () => {
        if (currentStep < steps.length - 1) {
            setCurrentStep((prev: number) => prev + 1);
        } else {
            handleClose();
        }
    };

    const handleBack = () => {
        if (currentStep > 0) {
            setCurrentStep((prev: number) => prev - 1);
        }
    };

    const handleClose = () => {
        setHasSeenTutorial(true);
        setShowTutorial(false);
    };

    // Create a path for SVG mask (Full screen rectangle with a "hole")
    const getMaskPath = () => {
        if (!targetRect) return '';
        const { left, top, width, height } = targetRect;
        const radius = 12;
        // Outer rectangle (screen) and inner rounded rectangle (hole) using winding rule
        return `M 0 0 h ${window.innerWidth} v ${window.innerHeight} h -${window.innerWidth} z 
                M ${left - 5 + radius} ${top - 5}
                h ${width + 10 - 2 * radius}
                a ${radius} ${radius} 0 0 1 ${radius} ${radius}
                v ${height + 10 - 2 * radius}
                a ${radius} ${radius} 0 0 1 -${radius} ${radius}
                h -${width + 10 - 2 * radius}
                a ${radius} ${radius} 0 0 1 -${radius} -${radius}
                v -${height + 10 - 2 * radius}
                a ${radius} ${radius} 0 0 1 ${radius} -${radius}
                z`;
    };

    return (
        <div style={{
            position: 'fixed',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            zIndex: 3000, // Even higher
            pointerEvents: 'none'
        }}>
            {/* SVG Backdrop with Hole */}
            <svg
                style={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    width: '100%',
                    height: '100%',
                    pointerEvents: 'auto' // Backdrop captures clicks
                }}
                onClick={e => e.stopPropagation()}
            >
                <motion.path
                    initial={false}
                    animate={{ d: (targetRect && step.position !== 'center') ? getMaskPath() : `M 0 0 h ${window.innerWidth} v ${window.innerHeight} h -${window.innerWidth} z` }}
                    fillRule="evenodd"
                    fill="rgba(0,0,0,0.7)"
                    transition={{ type: 'spring', damping: 25, stiffness: 200 }}
                />
            </svg>

            {/* Modal Dialog */}
            <div style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
                pointerEvents: 'none',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center'
            }}>
                <AnimatePresence mode="wait">
                    <motion.div
                        key={currentStep}
                        initial={{ opacity: 0, y: 20, scale: 0.95 }}
                        animate={{ opacity: 1, y: 0, scale: 1 }}
                        exit={{ opacity: 0, y: -20, scale: 0.95 }}
                        style={{
                            position: 'absolute',
                            ...(step.position === 'center' ? {
                                top: 0,
                                left: 0,
                                right: 0,
                                bottom: 0,
                                margin: 'auto',
                                width: '85%',
                                height: 'fit-content',
                                maxWidth: '340px'
                            } : step.position === 'top' ? {
                                bottom: '160px',
                                left: '20px',
                                right: '20px'
                            } : {
                                top: targetRect ? Math.min(targetRect.bottom + 20, window.innerHeight - 250) : '20%',
                                left: '20px',
                                right: '20px'
                            }),
                            backgroundColor: '#1e293b',
                            padding: '24px',
                            borderRadius: '24px',
                            border: '1px solid rgba(255,255,255,0.1)',
                            boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)',
                            pointerEvents: 'auto',
                            color: 'white',
                            zIndex: 3001
                        }}
                    >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
                            <div style={{
                                backgroundColor: '#3b82f6',
                                width: '36px',
                                height: '36px',
                                borderRadius: '12px',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center'
                            }}>
                                <Info size={20} />
                            </div>
                            <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 'bold' }}>{step.title}</h3>
                        </div>

                        <p style={{ margin: '0 0 24px 0', fontSize: '1rem', lineHeight: '1.6', opacity: 0.9 }}>
                            {step.content}
                        </p>

                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <button
                                onClick={handleClose}
                                style={{
                                    background: 'none',
                                    border: 'none',
                                    color: '#94a3b8',
                                    fontSize: '0.9rem',
                                    padding: '8px'
                                }}
                            >
                                スキップ
                            </button>

                            <div style={{ display: 'flex', gap: '10px' }}>
                                {currentStep > 0 && (
                                    <button
                                        onClick={handleBack}
                                        style={{
                                            backgroundColor: 'rgba(255,255,255,0.05)',
                                            border: '1px solid rgba(255,255,255,0.1)',
                                            color: 'white',
                                            padding: '10px 18px',
                                            borderRadius: '14px',
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: '6px',
                                            fontSize: '0.95rem'
                                        }}
                                    >
                                        <ChevronLeft size={18} />
                                        戻る
                                    </button>
                                )}
                                <button
                                    onClick={handleNext}
                                    style={{
                                        backgroundColor: '#3b82f6',
                                        border: 'none',
                                        color: 'white',
                                        padding: '10px 24px',
                                        borderRadius: '14px',
                                        fontWeight: 'bold',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '6px',
                                        fontSize: '0.95rem',
                                        boxShadow: '0 4px 12px rgba(59, 130, 246, 0.3)'
                                    }}
                                >
                                    {currentStep === steps.length - 1 ? '開始！' : '次へ'}
                                    <ChevronRight size={18} />
                                </button>
                            </div>
                        </div>

                        {/* Pagination dots */}
                        <div style={{ display: 'flex', gap: '8px', justifyContent: 'center', marginTop: '24px' }}>
                            {steps.map((_, i) => (
                                <div
                                    key={i}
                                    style={{
                                        width: i === currentStep ? '20px' : '8px',
                                        height: '8px',
                                        borderRadius: '4px',
                                        backgroundColor: i === currentStep ? '#3b82f6' : 'rgba(255,255,255,0.15)',
                                        transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)'
                                    }}
                                />
                            ))}
                        </div>
                    </motion.div>
                </AnimatePresence>
            </div>
        </div>
    );
};
