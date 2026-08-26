from __future__ import annotations


def build_system_prompt(lang: str) -> str:
    if lang.lower() == "zh-cn":
        return (
            "你是一个严谨的视觉识别与风险提示助手。"
            "你必须只输出一个 JSON 对象，不允许输出任何额外文字、代码块标记或解释。"
            "JSON 字段必须固定为："
            "label_main, confidence, candidates, risk_level, risk_tags, summary, advice, details。"
            "其中 candidates 为数组，每个元素包含 label/confidence。"
            "confidence 范围 0~1；risk_level 为 0~3 的整数。"
            "对植物场景必须优先给出“具体物种”而不是笼统的“植物/花/叶子”。"
            "若疑似有毒植物，必须在 risk_tags 中包含 toxic_plant_suspected，"
            "并在 summary/advice 里明确“避免接触/误食、远离儿童宠物”。"
            "若不确定，可输出疑似物种列表并降低 confidence，但不能省略风险判断。"
            "details 字段建议包含 botanical_traits(形态特征) 与 toxicity_evidence(毒性依据) 两个键。"
        )
    return (
        "You are a careful visual identifier and risk advisor. "
        "You must output ONLY one JSON object and nothing else. "
        "The JSON fields MUST be exactly: "
        "label_main, confidence, candidates, risk_level, risk_tags, summary, advice, details. "
        "candidates is an array of {label, confidence}. "
        "confidence is 0~1; risk_level is an integer 0~3."
    )


def build_user_prompt(scene: str, lang: str) -> str:
    if lang.lower() == "zh-cn":
        few_shot = (
            "参考输出风格示例（仅示例，不要照抄具体值）："
            '{"label_main":"夹竹桃","confidence":0.74,'
            '"candidates":[{"label":"夹竹桃","confidence":0.74},{"label":"长春花","confidence":0.41}],'
            '"risk_level":3,"risk_tags":["toxic_plant_suspected","cardiac_glycoside_risk"],'
            '"summary":"检测到疑似有毒植物（夹竹桃）。",'
            '"advice":"避免直接接触和误食，远离儿童与宠物，必要时联系专业人员确认。",'
            '"details":{"botanical_traits":["披针形叶片","花序成簇"],"toxicity_evidence":["夹竹桃全株有毒"]}}'
        )
        return (
            f"场景={scene}。请根据图片进行识别，并给出风险等级与建议。"
            "若是植物，请重点判断是否为有毒植物（例如夹竹桃、曼陀罗、乌头、蓖麻、马缨丹、毒芹、铃兰等），"
            "并尽量给出具体名称。"
            "如果无法确定，请给出最合理的候选并降低 confidence。"
            f"{few_shot}"
            "再次强调：只输出 JSON。"
        )
    return (
        f"scene={scene}. Identify based on the image and provide risk level and advice. "
        "If uncertain, provide best candidates and lower confidence. "
        "Output JSON only."
    )

