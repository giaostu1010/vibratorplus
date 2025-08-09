FFMPEG_PATH = "ffmpeg"

import numpy as np
import subprocess
import os
import librosa
import warnings
warnings.filterwarnings("ignore")

def extract_vibration_pattern(audio_path, output_txt="vibration_pattern.txt"):
    """
    提取音频低频信号并生成震动模式数组
    参数:
        audio_path: 输入音频文件路径
        output_txt: 输出文本文件名
    """
    # 临时文件名
    temp_file = "temp_audio.wav"
    
    # 使用FFmpeg转换音频为单通道WAV
    command = [
        FFMPEG_PATH,
        '-i', audio_path,
        '-ac', '1',          # 单声道
        '-ar', '22050',      # 采样率22.05kHz
        '-y',                # 覆盖现有文件
        temp_file
    ]
    subprocess.run(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    
    # 加载音频数据
    y, sr = librosa.load(temp_file, sr=22050, mono=True)
    # os.remove(temp_file)  # 删除临时文件
    
    # 参数设置
    frame_size = 1024       # 每帧样本数
    hop_length = 512        # 帧移
    low_freq_max = 200      # 最大低频阈值(Hz)
    threshold_ratio = 1.5   # 能量阈值倍数
    
    # 计算每帧的低频能量
    frame_energies = []
    for i in range(0, len(y) - frame_size, hop_length):
        frame = y[i:i + frame_size]
        
        # 计算FFT
        fft = np.fft.rfft(frame * np.hamming(frame_size))
        magnitudes = np.abs(fft)
        
        # 计算频率轴
        freqs = np.fft.rfftfreq(frame_size, d=1/sr)
        
        # 提取低频能量 (0-200Hz)
        low_freq_mask = (freqs >= 0) & (freqs <= low_freq_max)
        energy = np.sum(magnitudes[low_freq_mask] ** 2)
        frame_energies.append(energy)
    
    # 设置动态阈值
    avg_energy = np.mean(frame_energies)
    threshold = avg_energy * threshold_ratio
    
    # 创建震动标记序列 (1=震动, 0=静音)
    vibration_flags = [1 if energy >= threshold else 0 for energy in frame_energies]
    
    # 计算帧持续时间(毫秒)
    frame_duration = (hop_length / sr) * 1000
    
    # 合并连续区段并计算持续时间
    sections = []
    current_flag = vibration_flags[0]
    current_duration = frame_duration
    
    for flag in vibration_flags[1:]:
        if flag == current_flag:
            current_duration += frame_duration
        else:
            sections.append((current_flag, round(current_duration)))
            current_flag = flag
            current_duration = frame_duration
    
    sections.append((current_flag, round(current_duration)))
    
    # 生成震动模式数组 [等待, 震动, 等待, 震动...]
    vibration_pattern = []
    if sections[0][0] == 1:  # 如果以震动开始
        vibration_pattern.append(0)  # 添加0ms等待
    
    for flag, duration in sections:
        vibration_pattern.append(duration)
    
    # 保存到文本文件
    with open(output_txt, 'w') as f:
        f.write('{ ' + ', '.join(map(str, vibration_pattern)) + ' }')
    
    return vibration_pattern

# 使用示例
if __name__ == "__main__":
    audio_file = input("请输入音频文件路径: ")  # 替换为你的音频文件
    pattern = extract_vibration_pattern(audio_file)
    print("生成的震动模式数组:", pattern)
    print("结果已保存到 vibration_pattern.txt")