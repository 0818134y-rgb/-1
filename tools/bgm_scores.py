"""わちゃパの場面別BGMの楽譜（作曲: Claude）。tools/make_bgm.py が読んで音にする。

書き方
- chords: 1小節に1つのコード。
- melody / counter: 「音名オクターブ:拍数」をスペースで区切って並べる（休符は R）。セクションの小節数×4拍ちょうど。
- lead: メロディの楽器。リストにすると重ねる（'pluck:-1' は1オクターブ下）。
- harmony: メロディの3度下でハモる楽器。
- pad: 'pad' 'warm' 'organ' 'epiano' 'stabs' 'none'。arp: (楽器, 並び, 1拍の音の数)。
- bass: 'bounce' 'drive' 'pulse' 'walk' 'root' 'long' 'lofi' 'none'。drums: make_bgm.GROOVES の名前。
key は調の主音（C=0 … B=11）、mode は 'major' か 'minor'（ハモりの計算に使う）。
"""

TRACKS = [
    # ------------------------------------------------------------ タイトル（ホーム画面・遊び方）
    dict(id='title', bpm=128, key=2, mode='major', duck=0.35, reverb=0.22, delay=0.16, loud_db=-17, sections=[
        dict(  # A: 主題
            chords=['D', 'A', 'Bm', 'F#m', 'G', 'D', 'Em7', 'A7'],
            melody='F#5:0.5 A5:0.5 D6:1 C#6:0.5 D6:0.5 R:0.5 A5:0.5 '
                   'C#6:0.5 B5:0.5 A5:1 E5:1 R:1 '
                   'D5:0.5 F#5:0.5 B5:1 A5:0.5 B5:0.5 R:0.5 F#5:0.5 '
                   'A5:1.5 F#5:0.5 C#5:2 '
                   'B5:0.5 D6:0.5 E6:1 D6:0.5 B5:0.5 R:0.5 G5:0.5 '
                   'A5:1 F#5:0.5 A5:0.5 D6:2 '
                   'G5:0.5 F#5:0.5 E5:0.5 G5:0.5 B5:1 A5:0.5 G5:0.5 '
                   'E5:1 F#5:0.5 G5:0.5 A5:1 R:1',
            lead=['square', 'pluck:-1'], double_gain=0.5, pad='pad', arp=('pluck', 'up', 4), arp_gain=0.6,
            bass='bounce', drums='pop', fill='snare'),
        dict(  # B: サビ
            chords=['G', 'A', 'F#m7', 'Bm7', 'G', 'A', 'D/F#', 'A7'],
            melody='D6:1.5 B5:0.5 D6:1 E6:1 '
                   'C#6:1.5 A5:0.5 E5:1 A5:1 '
                   'C#6:0.5 B5:0.5 A5:0.5 C#6:0.5 E6:1 C#6:1 '
                   'D6:2 B5:1 R:1 '
                   'B5:0.5 C#6:0.5 D6:1 E6:0.5 D6:0.5 B5:1 '
                   'C#6:0.5 D6:0.5 E6:1 A6:1.5 R:0.5 '
                   'F#6:1 E6:0.5 D6:0.5 A5:1 F#5:1 '
                   'G5:0.5 A5:0.5 B5:0.5 C#6:0.5 E6:1 R:1',
            lead=['brass', 'square'], double_gain=0.55, pad='stabs', comp=[(0, 0.4), (1.5, 0.4), (2.5, 0.4), (3.5, 0.3)],
            arp=('bell', 'broken', 2), arp_gain=0.5, bass='drive', drums='four', fill='tom'),
        dict(  # A': 主題をハモりで
            chords=['D', 'A', 'Bm', 'F#m', 'G', 'D', 'Em7', 'A7'],
            melody='F#5:0.5 A5:0.5 D6:1 C#6:0.5 D6:0.5 R:0.5 A5:0.5 '
                   'C#6:0.5 B5:0.5 A5:1 E5:1 R:1 '
                   'D5:0.5 F#5:0.5 B5:1 A5:0.5 B5:0.5 R:0.5 F#5:0.5 '
                   'A5:1.5 F#5:0.5 C#5:2 '
                   'B5:0.5 D6:0.5 E6:1 D6:0.5 B5:0.5 R:0.5 G5:0.5 '
                   'A5:1 F#5:0.5 A5:0.5 D6:2 '
                   'G5:0.5 F#5:0.5 E5:0.5 G5:0.5 B5:1 C#6:0.5 D6:0.5 '
                   'E6:1 D6:0.5 C#6:0.5 A5:2',
            lead=['square', 'bell'], double_gain=0.35, harmony='triangle', harmony_gain=0.45, pad='pad',
            arp=('pluck', 'updown', 4), arp_gain=0.6, bass='bounce', drums='pop', fill='snare'),
        dict(  # C: ひと休み（グロッケンとパッド）→ 主題へ戻る
            chords=['Bm', 'G', 'D', 'A', 'Bm', 'G', 'Em7', 'A7'],
            melody='B5:2 F#5:2 G5:2 D6:2 F#5:2 A5:2 E5:3 R:1 '
                   'D6:2 B5:2 B5:2 G5:2 E6:2 D6:1 B5:1 '
                   'C#6:1 B5:0.5 A5:0.5 E5:0.5 F#5:0.5 G5:0.5 A5:0.5',
            lead=['bell', 'flute:-1'], double_gain=0.4, pad='warm', pad_gain=0.5, arp=('marimba', 'broken', 2), arp_gain=0.55,
            bass='long', bass_inst='sub', bass_gain=0.55, drums='soft', drum_gain=0.8, fill='soft', crash=False),
    ]),

    # ------------------------------------------------------------ ロビー（部屋で待つ）
    dict(id='lobby', bpm=100, key=5, mode='major', swing=0.12, duck=0.15, reverb=0.26, delay=0.12, loud_db=-18.5, sections=[
        dict(
            chords=['Fmaj7', 'Dm7', 'Gm7', 'C7', 'Fmaj7', 'Am7', 'Bbmaj7', 'C7'],
            melody='A5:0.5 C6:0.5 R:0.5 A5:0.5 G5:0.5 F5:0.5 R:1 '
                   'F5:0.5 A5:0.5 D6:1 C6:0.5 A5:0.5 R:1 '
                   'Bb5:0.5 A5:0.5 G5:0.5 F5:0.5 G5:1 R:1 '
                   'E5:0.5 G5:0.5 Bb5:1 A5:0.5 G5:0.5 R:1 '
                   'A5:0.5 C6:0.5 R:0.5 A5:0.5 G5:0.5 F5:0.5 R:0.5 E5:0.5 '
                   'E5:0.5 G5:0.5 C6:1 E6:1 R:1 '
                   'D6:0.5 C6:0.5 A5:0.5 F5:0.5 D6:1 C6:0.5 Bb5:0.5 '
                   'G5:1 E5:1 R:2',
            lead=['marimba', 'pluck:-1'], double_gain=0.35, legato=0.8, pad='epiano', bass='walk', bass_inst='finger',
            drums='shuffle', drum_gain=0.75, fill='soft'),
        dict(
            chords=['Fmaj7', 'Dm7', 'Gm7', 'C7', 'Am7', 'D7', 'Gm7', 'C7'],
            melody='A5:0.5 C6:0.5 R:0.5 A5:0.5 G5:0.5 F5:0.5 R:1 '
                   'F5:0.5 A5:0.5 D6:1 C6:0.5 A5:0.5 R:1 '
                   'Bb5:0.5 A5:0.5 G5:0.5 F5:0.5 G5:1 R:1 '
                   'E5:0.5 G5:0.5 Bb5:1 A5:0.5 G5:0.5 R:1 '
                   'C6:0.5 E6:0.5 R:0.5 C6:0.5 B5:0.5 A5:0.5 R:1 '
                   'F#5:0.5 A5:0.5 C6:1 D6:1 R:1 '
                   'Bb5:0.5 D6:0.5 F6:0.5 D6:0.5 C6:1 Bb5:1 '
                   'G5:1 E5:0.5 G5:0.5 C6:1 R:1',
            lead=['marimba', 'bell'], double_gain=0.3, legato=0.8, harmony='marimba', harmony_gain=0.35, pad='epiano',
            bass='walk', bass_inst='finger', drums='shuffle', drum_gain=0.8, fill='snare', crash=False),
        dict(
            chords=['Bbmaj7', 'C', 'Am7', 'Dm7', 'Gm7', 'C7', 'Fmaj7', 'C7'],
            melody='D6:2 C6:1 Bb5:1 C6:3 R:1 E6:1.5 D6:0.5 C6:1 A5:1 D6:3 R:1 '
                   'Bb5:1 C6:1 D6:1 F6:1 E6:2 G5:1 Bb5:1 A5:1 C6:1 E6:1.5 F6:0.5 '
                   'E6:1 D6:0.5 C6:0.5 Bb5:0.5 A5:0.5 G5:1',
            lead=['flute'], legato=0.95, counter='F4:2 D4:2 E4:4 C5:2 A4:2 F4:4 D4:2 F4:2 G4:4 A4:4 G4:2 E4:2',
            counter_inst='epiano', counter_gain=1.2, pad='warm', pad_gain=0.8, bass='root', bass_inst='finger',
            drums='shuffle', drum_gain=0.7, fill='snare'),
    ]),

    # ------------------------------------------------------------ 話し合い（声のじゃまをしない）
    dict(id='talk', bpm=90, key=9, mode='minor', duck=0.0, reverb=0.32, delay=0.22, loud_db=-21.5, room=1.15, sections=[
        dict(
            chords=['Am9', 'Fmaj7', 'Am9', 'Fmaj7', 'Dm9', 'Em7', 'Fmaj7', 'E7'],
            pad='warm', pad_gain=0.9, arp=('pluck', 'pulse', 2), arp_gain=0.55, arp_oct=0,
            bass='long', bass_inst='sub', bass_gain=0.8, drums='clock', drum_gain=0.8, fill='none', crash=False),
        dict(
            chords=['Am9', 'Fmaj7', 'Am9', 'Fmaj7', 'Dm9', 'Em7', 'Fmaj7', 'E7'],
            melody='R:2 E6:1 B5:1 C6:3 R:1 R:2 A5:1 C6:1 E6:3 R:1 '
                   'R:1 F6:1 E6:1 D6:1 B5:3 R:1 R:2 A5:1 C6:1 B5:2 G#5:2',
            lead=['bell'], lead_gain=0.75, pad='warm', pad_gain=0.9, arp=('pluck', 'pulse', 2), arp_gain=0.5, arp_oct=0,
            bass='long', bass_inst='sub', bass_gain=0.8, drums='soft', drum_gain=0.45, fill='none', crash=False),
        dict(
            chords=['Am9', 'Fmaj7', 'Am9', 'Fmaj7', 'Dm9', 'Em7', 'Fmaj7', 'E7'],
            melody='R:2 E6:1 G6:1 A6:2 E6:2 R:2 C6:1 E6:1 D6:1 C6:1 A5:2 '
                   'R:1 A5:1 C6:1 F6:1 E6:2 D6:1 B5:1 C6:2 A5:2 G#5:2 B5:1 R:1',
            lead=['bell', 'marimba'], lead_gain=0.7, double_gain=0.4, pad='warm', pad_gain=0.9,
            arp=('pluck', 'wide', 2), arp_gain=0.5, arp_oct=0, bass='long', bass_inst='sub', bass_gain=0.8,
            drums='soft', drum_gain=0.45, fill='none', crash=False),
        dict(
            chords=['Dm9', 'Em7', 'Fmaj7', 'E7', 'Dm9', 'Em7', 'Fmaj7', 'E7'],
            pad='warm', pad_gain=0.9, arp=('marimba', 'broken', 2), arp_gain=0.45, arp_oct=0,
            bass='long', bass_inst='sub', bass_gain=0.8, drums='clock', drum_gain=0.8, fill='none', crash=False),
    ]),

    # ------------------------------------------------------------ 投票・犯人当て（ドキドキ）
    dict(id='tension', bpm=116, key=2, mode='minor', duck=0.25, reverb=0.24, delay=0.14, loud_db=-18, sections=[
        dict(  # 刻みだけ
            chords=['Dm', 'Dm', 'Bb', 'A', 'Dm', 'Dm', 'Gm', 'A7'],
            pad='pad', pad_gain=0.7, arp=('stacc', 'pulse', 4), arp_gain=0.8, arp_oct=0,
            bass='pulse', bass_inst='bass', drums='tension', drum_gain=0.7, fill='tom'),
        dict(  # 主題
            chords=['Dm', 'Dm', 'Bb', 'A', 'Dm', 'Dm', 'Gm', 'A7'],
            melody='D5:0.5 R:0.5 A5:0.5 R:0.5 F5:1 E5:1 '
                   'D5:0.5 R:0.5 A5:0.5 R:0.5 Bb5:1 A5:1 '
                   'D6:1 C6:0.5 Bb5:0.5 F5:2 '
                   'E5:1 A5:1 C#6:1 E6:1 '
                   'F6:1.5 E6:0.5 D6:1 A5:1 '
                   'F5:0.5 G5:0.5 A5:1 D6:1 C6:1 '
                   'Bb5:1 D6:1 G6:1 F6:0.5 D6:0.5 '
                   'C#6:1 E6:1 G5:1 R:1',
            lead=['brass', 'stacc'], double_gain=0.5, pad='pad', pad_gain=0.7, arp=('stacc', 'pulse', 4), arp_gain=0.7,
            arp_oct=0, bass='pulse', drums='tension', drum_gain=0.85, fill='snare'),
        dict(  # 盛り上がり
            chords=['Bb', 'C', 'A', 'Dm', 'Bb', 'C', 'Eb', 'A7'],
            melody='D6:2 F6:2 E6:2 G6:2 E6:3 R:1 F6:1 E6:1 D6:1 A5:1 '
                   'D6:1.5 C6:0.5 Bb5:1 D6:1 C6:1.5 Bb5:0.5 G5:1 E5:1 Eb6:2 Bb5:2 C#6:1 E6:1 A5:2',
            lead=['brass', 'square'], double_gain=0.4, harmony='brass', harmony_gain=0.35, pad='pad',
            arp=('stacc', 'updown', 4), arp_gain=0.7, arp_oct=0, bass='drive', drums='drive', fill='tom'),
    ]),

    # ------------------------------------------------------------ なんでもアリーナ（にぎやかなカードバトル）
    dict(id='arena', bpm=140, key=7, mode='major', duck=0.35, reverb=0.18, delay=0.12, loud_db=-16.5, sections=[
        dict(
            chords=['G', 'Em', 'C', 'D', 'G', 'B7', 'Em', 'D'],
            melody='G5:0.5 B5:0.5 D6:0.5 B5:0.5 G5:0.5 A5:0.5 B5:1 '
                   'E5:0.5 G5:0.5 B5:0.5 G5:0.5 E5:0.5 F#5:0.5 G5:1 '
                   'C6:0.5 B5:0.5 A5:0.5 G5:0.5 E5:0.5 G5:0.5 C6:1 '
                   'D6:0.5 C6:0.5 B5:0.5 A5:0.5 F#5:1 D5:1 '
                   'G5:0.5 B5:0.5 D6:0.5 G6:0.5 F#6:0.5 D6:0.5 B5:1 '
                   'B5:0.5 D#6:0.5 F#6:0.5 D#6:0.5 B5:0.5 A5:0.5 F#5:1 '
                   'G5:0.5 F#5:0.5 E5:0.5 G5:0.5 B5:1 E6:1 '
                   'D6:1 R:0.5 D6:0.5 E6:0.5 F#6:0.5 R:1',
            lead=['square', 'square:-1'], double_gain=0.35, legato=0.75, pad='stabs', comp=[(0, 0.35), (1.5, 0.35), (3, 0.35)],
            arp=('pluck', 'up', 4), arp_gain=0.5, bass='bounce', drums='drive', fill='tom'),
        dict(
            chords=['C', 'D', 'Bm7', 'Em', 'Am7', 'D', 'G', 'D7'],
            melody='E6:1 R:0.5 E6:0.5 D6:0.5 C6:0.5 G5:1 '
                   'F#6:1 R:0.5 F#6:0.5 E6:0.5 D6:0.5 A5:1 '
                   'D6:0.5 B5:0.5 D6:0.5 F#6:0.5 E6:1 D6:1 '
                   'B5:2 G5:1 E5:1 '
                   'C6:0.5 E6:0.5 G6:0.5 E6:0.5 C6:0.5 E6:0.5 A5:1 '
                   'F#6:0.5 E6:0.5 D6:0.5 C6:0.5 A5:0.5 F#5:0.5 D5:1 '
                   'G5:1 B5:1 D6:1 G6:1 '
                   'F#6:0.5 E6:0.5 D6:0.5 C6:0.5 B5:0.5 A5:0.5 F#5:0.5 A5:0.5',
            lead=['brass', 'square'], double_gain=0.45, pad='pad', pad_gain=0.8, arp=('bell', 'broken', 2), arp_gain=0.5,
            bass='drive', drums='four', fill='snare'),
        dict(
            chords=['G', 'Em', 'C', 'D', 'G', 'B7', 'Em', 'D'],
            melody='G5:0.5 B5:0.5 D6:0.5 B5:0.5 G5:0.5 A5:0.5 B5:1 '
                   'E5:0.5 G5:0.5 B5:0.5 G5:0.5 E5:0.5 F#5:0.5 G5:1 '
                   'C6:0.5 B5:0.5 A5:0.5 G5:0.5 E5:0.5 G5:0.5 C6:1 '
                   'D6:0.5 C6:0.5 B5:0.5 A5:0.5 F#5:1 D5:1 '
                   'G5:0.5 B5:0.5 D6:0.5 G6:0.5 F#6:0.5 D6:0.5 B5:1 '
                   'B5:0.5 D#6:0.5 F#6:0.5 D#6:0.5 B5:0.5 A5:0.5 F#5:1 '
                   'G5:0.5 F#5:0.5 E5:0.5 G5:0.5 B5:1 E6:1 '
                   'D6:1 R:0.5 D6:0.5 E6:0.5 F#6:0.5 R:1',
            lead=['square', 'bell'], double_gain=0.3, legato=0.75, harmony='triangle', harmony_gain=0.45, pad='stabs',
            comp=[(0, 0.35), (1.5, 0.35), (3, 0.35)], arp=('pluck', 'updown', 4), arp_gain=0.5, bass='bounce',
            drums='drive', fill='tom'),
        dict(  # ひと息（マリンバ）→ 上がっていって頭へ
            chords=['G', 'Em', 'C', 'D', 'G', 'B7', 'Em', 'D'],
            melody='B5:1 D6:1 G5:2 G5:1 B5:1 E5:2 E6:1 C6:1 G5:2 F#5:1 A5:1 D6:2 '
                   'B5:1 D6:1 G6:2 F#6:1 D#6:1 B5:2 E6:1 B5:1 G5:1 E5:1 '
                   'D5:0.5 E5:0.5 F#5:0.5 G5:0.5 A5:0.5 B5:0.5 C6:0.5 C#6:0.5',
            lead=['marimba', 'pluck:-1'], double_gain=0.4, pad='warm', pad_gain=0.45, arp=('pluck', 'broken', 2),
            arp_gain=0.35, bass='root', bass_inst='finger', bass_gain=0.6, drums='halftime', drum_gain=0.75, fill='build',
            crash=False),
    ]),

    # ------------------------------------------------------------ お絵かき（描いている間）
    dict(id='draw', bpm=108, key=0, mode='major', swing=0.1, duck=0.15, reverb=0.24, delay=0.15, loud_db=-19, sections=[
        dict(
            chords=['C', 'Am', 'Dm7', 'G7', 'C', 'E7', 'Am', 'G'],
            melody='E5:0.5 G5:0.5 C6:0.5 R:0.5 B5:0.5 C6:0.5 R:1 '
                   'A5:0.5 C6:0.5 E6:0.5 R:0.5 D6:0.5 C6:0.5 R:1 '
                   'F5:0.5 A5:0.5 D6:0.5 C6:0.5 A5:0.5 F5:0.5 D5:1 '
                   'G5:1 B5:0.5 D6:0.5 F6:1 R:1 '
                   'E6:0.5 D6:0.5 C6:0.5 G5:0.5 E5:0.5 G5:0.5 C6:1 '
                   'B5:0.5 G#5:0.5 E5:0.5 G#5:0.5 B5:1 D6:1 '
                   'C6:0.5 B5:0.5 A5:0.5 E5:0.5 A5:1 C6:1 '
                   'B5:1 A5:0.5 G5:0.5 D5:1 R:1',
            lead=['pluck', 'marimba'], double_gain=0.3, legato=0.7, pad='warm', pad_gain=0.7,
            bass='bounce', bass_inst='finger', drums='soft', drum_gain=0.8, fill='soft'),
        dict(
            chords=['C', 'Am', 'Dm7', 'G7', 'C', 'E7', 'Am', 'G'],
            melody='E5:0.5 G5:0.5 C6:0.5 R:0.5 B5:0.5 C6:0.5 R:1 '
                   'A5:0.5 C6:0.5 E6:0.5 R:0.5 D6:0.5 C6:0.5 R:1 '
                   'F5:0.5 A5:0.5 D6:0.5 C6:0.5 A5:0.5 F5:0.5 D5:1 '
                   'G5:1 B5:0.5 D6:0.5 F6:1 R:1 '
                   'E6:0.5 D6:0.5 C6:0.5 G5:0.5 E5:0.5 G5:0.5 C6:1 '
                   'B5:0.5 G#5:0.5 E5:0.5 G#5:0.5 B5:1 D6:1 '
                   'C6:0.5 B5:0.5 A5:0.5 E5:0.5 A5:1 C6:1 '
                   'B5:1 A5:0.5 G5:0.5 D5:1 R:1',
            counter='C5:2 E5:2 C5:2 A4:2 D5:2 F5:2 B4:2 D5:2 C5:2 G4:2 G#4:2 B4:2 A4:2 C5:2 B4:2 G4:2',
            counter_inst='bell', counter_gain=0.8,
            lead=['marimba', 'pluck:-1'], double_gain=0.4, legato=0.7, pad='warm', pad_gain=0.7,
            bass='bounce', bass_inst='finger', drums='shuffle', drum_gain=0.7, fill='snare'),
        dict(
            chords=['F', 'G', 'Em7', 'Am', 'Dm7', 'G7', 'C', 'G7'],
            melody='A5:2 C6:1 F6:1 D6:2 B5:1 G5:1 G5:1 B5:1 D6:1 E6:1 C6:3 R:1 '
                   'F6:1.5 E6:0.5 D6:1 C6:1 B5:2 D6:1 F6:1 E6:2 C6:1 G5:1 D6:1 B5:1 G5:1 R:1',
            lead=['flute'], legato=0.95, pad='epiano', comp=[(0, 1.0), (1.5, 1.0), (2.5, 1.5)], pad_gain=0.9,
            arp=('pluck', 'broken', 2), arp_gain=0.45, bass='walk', bass_inst='finger', drums='shuffle', drum_gain=0.7,
            fill='snare'),
        dict(
            chords=['C', 'Am', 'Dm7', 'G7', 'C', 'E7', 'Am', 'G7'],
            melody='E5:0.5 G5:0.5 C6:0.5 R:0.5 B5:0.5 C6:0.5 R:1 '
                   'A5:0.5 C6:0.5 E6:0.5 R:0.5 D6:0.5 C6:0.5 R:1 '
                   'F5:0.5 A5:0.5 D6:0.5 C6:0.5 A5:0.5 F5:0.5 D5:1 '
                   'G5:1 B5:0.5 D6:0.5 F6:1 R:1 '
                   'E6:0.5 D6:0.5 C6:0.5 G5:0.5 E5:0.5 G5:0.5 C6:1 '
                   'B5:0.5 G#5:0.5 E5:0.5 G#5:0.5 B5:1 D6:1 '
                   'C6:0.5 B5:0.5 A5:0.5 E5:0.5 A5:1 C6:1 '
                   'B5:1 G5:0.5 A5:0.5 B5:0.5 D6:0.5 R:1',
            lead=['pluck', 'bell'], double_gain=0.3, legato=0.7, harmony='marimba', harmony_gain=0.4, pad='warm',
            pad_gain=0.7, bass='bounce', bass_inst='finger', drums='soft', drum_gain=0.8, fill='soft', crash=False),
    ]),

    # ------------------------------------------------------------ 結果発表
    dict(id='result', bpm=120, key=7, mode='major', duck=0.3, reverb=0.24, delay=0.15, loud_db=-17.5, sections=[
        dict(
            chords=['G', 'D/F#', 'Em', 'C', 'G', 'D', 'C', 'D'],
            melody='D6:1 B5:0.5 D6:0.5 G6:2 '
                   'F#6:1 E6:0.5 D6:0.5 A5:2 '
                   'B5:1 C6:0.5 D6:0.5 E6:1 D6:1 '
                   'C6:1 B5:0.5 A5:0.5 G5:2 '
                   'B5:0.5 C6:0.5 D6:1 G6:1 D6:1 '
                   'F#6:1 E6:0.5 F#6:0.5 D6:2 '
                   'E6:1 D6:0.5 C6:0.5 G5:1 A5:1 '
                   'A5:1 B5:0.5 C6:0.5 D6:1 R:1',
            lead=['bell', 'pluck'], double_gain=0.6, pad='pad', pad_gain=0.8, arp=('pluck', 'up', 2), arp_gain=0.5,
            bass='bounce', drums='pop', drum_gain=0.85, fill='snare'),
        dict(
            chords=['Em', 'C', 'G', 'D', 'C', 'D', 'G', 'D7'],
            melody='E6:1.5 D6:0.5 B5:2 C6:1.5 D6:0.5 E6:2 D6:1.5 B5:0.5 G5:2 A5:1 B5:1 D6:2 '
                   'E6:1 G6:1 E6:1 C6:1 F#6:1 A6:1 F#6:1 D6:1 B5:1 D6:1 G6:2 '
                   'F#6:0.5 E6:0.5 D6:0.5 C6:0.5 A5:1 R:1',
            lead=['brass', 'square'], double_gain=0.4, pad='stabs', comp=[(0, 0.5), (2, 0.5), (2.5, 0.5)],
            arp=('bell', 'broken', 2), arp_gain=0.5, bass='drive', drums='four', fill='tom'),
        dict(
            chords=['G', 'D/F#', 'Em', 'C', 'G', 'D', 'C', 'D'],
            melody='D6:1 B5:0.5 D6:0.5 G6:2 '
                   'F#6:1 E6:0.5 D6:0.5 A5:2 '
                   'B5:1 C6:0.5 D6:0.5 E6:1 D6:1 '
                   'C6:1 B5:0.5 A5:0.5 G5:2 '
                   'B5:0.5 C6:0.5 D6:1 G6:1 D6:1 '
                   'F#6:1 E6:0.5 F#6:0.5 D6:2 '
                   'E6:1 D6:0.5 C6:0.5 G5:1 A5:1 '
                   'A5:1 B5:0.5 C6:0.5 D6:1 R:1',
            lead=['square', 'bell'], double_gain=0.4, harmony='triangle', harmony_gain=0.45, pad='pad', pad_gain=0.8,
            arp=('pluck', 'updown', 4), arp_gain=0.45, bass='bounce', drums='pop', fill='snare'),
    ]),

    # ------------------------------------------------------------ 休憩（広告の画面）
    dict(id='break', bpm=84, key=2, mode='major', swing=0.14, duck=0.2, reverb=0.3, delay=0.2, loud_db=-19.5, lofi=True,
         sections=[
        dict(
            chords=['Dmaj7', 'Bm7', 'Em7', 'A7', 'Dmaj7', 'Bm7', 'Gmaj7', 'A7'],
            melody='F#5:1.5 A5:0.5 C#6:2 D6:1 C#6:0.5 B5:0.5 A5:2 G5:1 B5:1 D6:1 E6:1 C#6:3 R:1 '
                   'F#5:1.5 A5:0.5 E6:2 D6:1 B5:1 F#5:2 B5:1 D6:1 F#6:1 E6:1 C#6:2 R:2',
            lead=['epiano'], lead_gain=1.1, pad='epiano', comp=[(0, 1.4), (2.5, 1.2)], pad_gain=0.8,
            bass='lofi', bass_inst='sub', drums='lofi', drum_gain=0.75, fill='none', crash=False),
        dict(
            chords=['Gmaj7', 'F#m7', 'Em7', 'A7', 'Dmaj7', 'Bm7', 'Em7', 'A7'],
            melody='R:1 B5:1 D6:1 F#6:1 E6:2 C#6:2 D6:1 B5:1 G5:1 B5:1 A5:3 R:1 '
                   'F#6:1.5 E6:0.5 C#6:2 D6:1 C#6:1 B5:1 A5:1 G5:1 A5:1 B5:1 D6:1 C#6:1 B5:0.5 A5:0.5 R:2',
            lead=['flute'], lead_gain=0.8, legato=0.95, pad='epiano', comp=[(0, 1.4), (2.5, 1.2)], pad_gain=0.8,
            bass='lofi', bass_inst='sub', drums='lofi', drum_gain=0.75, fill='none', crash=False),
    ]),
]
