package se.atte.bragwise.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.flag_ad
import bragwise.shared.generated.resources.flag_ae
import bragwise.shared.generated.resources.flag_af
import bragwise.shared.generated.resources.flag_ag
import bragwise.shared.generated.resources.flag_ai
import bragwise.shared.generated.resources.flag_al
import bragwise.shared.generated.resources.flag_am
import bragwise.shared.generated.resources.flag_ao
import bragwise.shared.generated.resources.flag_ar
import bragwise.shared.generated.resources.flag_as
import bragwise.shared.generated.resources.flag_at
import bragwise.shared.generated.resources.flag_au
import bragwise.shared.generated.resources.flag_aw
import bragwise.shared.generated.resources.flag_az
import bragwise.shared.generated.resources.flag_ba
import bragwise.shared.generated.resources.flag_bb
import bragwise.shared.generated.resources.flag_bd
import bragwise.shared.generated.resources.flag_be
import bragwise.shared.generated.resources.flag_bf
import bragwise.shared.generated.resources.flag_bg
import bragwise.shared.generated.resources.flag_bh
import bragwise.shared.generated.resources.flag_bi
import bragwise.shared.generated.resources.flag_bj
import bragwise.shared.generated.resources.flag_bm
import bragwise.shared.generated.resources.flag_bn
import bragwise.shared.generated.resources.flag_bo
import bragwise.shared.generated.resources.flag_br
import bragwise.shared.generated.resources.flag_bs
import bragwise.shared.generated.resources.flag_bt
import bragwise.shared.generated.resources.flag_bw
import bragwise.shared.generated.resources.flag_by
import bragwise.shared.generated.resources.flag_bz
import bragwise.shared.generated.resources.flag_ca
import bragwise.shared.generated.resources.flag_cd
import bragwise.shared.generated.resources.flag_cf
import bragwise.shared.generated.resources.flag_cg
import bragwise.shared.generated.resources.flag_ch
import bragwise.shared.generated.resources.flag_ci
import bragwise.shared.generated.resources.flag_ck
import bragwise.shared.generated.resources.flag_cl
import bragwise.shared.generated.resources.flag_cm
import bragwise.shared.generated.resources.flag_cn
import bragwise.shared.generated.resources.flag_co
import bragwise.shared.generated.resources.flag_cr
import bragwise.shared.generated.resources.flag_cu
import bragwise.shared.generated.resources.flag_cv
import bragwise.shared.generated.resources.flag_cw
import bragwise.shared.generated.resources.flag_cy
import bragwise.shared.generated.resources.flag_cz
import bragwise.shared.generated.resources.flag_de
import bragwise.shared.generated.resources.flag_dj
import bragwise.shared.generated.resources.flag_dk
import bragwise.shared.generated.resources.flag_dm
import bragwise.shared.generated.resources.flag_do
import bragwise.shared.generated.resources.flag_dz
import bragwise.shared.generated.resources.flag_ec
import bragwise.shared.generated.resources.flag_ee
import bragwise.shared.generated.resources.flag_eg
import bragwise.shared.generated.resources.flag_er
import bragwise.shared.generated.resources.flag_es
import bragwise.shared.generated.resources.flag_et
import bragwise.shared.generated.resources.flag_fi
import bragwise.shared.generated.resources.flag_fj
import bragwise.shared.generated.resources.flag_fo
import bragwise.shared.generated.resources.flag_fr
import bragwise.shared.generated.resources.flag_ga
import bragwise.shared.generated.resources.flag_gb
import bragwise.shared.generated.resources.flag_gb_eng
import bragwise.shared.generated.resources.flag_gb_nir
import bragwise.shared.generated.resources.flag_gb_sct
import bragwise.shared.generated.resources.flag_gb_wls
import bragwise.shared.generated.resources.flag_gd
import bragwise.shared.generated.resources.flag_ge
import bragwise.shared.generated.resources.flag_gh
import bragwise.shared.generated.resources.flag_gi
import bragwise.shared.generated.resources.flag_gm
import bragwise.shared.generated.resources.flag_gn
import bragwise.shared.generated.resources.flag_gq
import bragwise.shared.generated.resources.flag_gr
import bragwise.shared.generated.resources.flag_gt
import bragwise.shared.generated.resources.flag_gu
import bragwise.shared.generated.resources.flag_gw
import bragwise.shared.generated.resources.flag_gy
import bragwise.shared.generated.resources.flag_hk
import bragwise.shared.generated.resources.flag_hn
import bragwise.shared.generated.resources.flag_hr
import bragwise.shared.generated.resources.flag_ht
import bragwise.shared.generated.resources.flag_hu
import bragwise.shared.generated.resources.flag_id
import bragwise.shared.generated.resources.flag_ie
import bragwise.shared.generated.resources.flag_il
import bragwise.shared.generated.resources.flag_in
import bragwise.shared.generated.resources.flag_iq
import bragwise.shared.generated.resources.flag_ir
import bragwise.shared.generated.resources.flag_is
import bragwise.shared.generated.resources.flag_it
import bragwise.shared.generated.resources.flag_je
import bragwise.shared.generated.resources.flag_jm
import bragwise.shared.generated.resources.flag_jo
import bragwise.shared.generated.resources.flag_jp
import bragwise.shared.generated.resources.flag_ke
import bragwise.shared.generated.resources.flag_kg
import bragwise.shared.generated.resources.flag_kh
import bragwise.shared.generated.resources.flag_km
import bragwise.shared.generated.resources.flag_kn
import bragwise.shared.generated.resources.flag_kp
import bragwise.shared.generated.resources.flag_kr
import bragwise.shared.generated.resources.flag_kw
import bragwise.shared.generated.resources.flag_ky
import bragwise.shared.generated.resources.flag_kz
import bragwise.shared.generated.resources.flag_la
import bragwise.shared.generated.resources.flag_lb
import bragwise.shared.generated.resources.flag_lc
import bragwise.shared.generated.resources.flag_li
import bragwise.shared.generated.resources.flag_lk
import bragwise.shared.generated.resources.flag_lr
import bragwise.shared.generated.resources.flag_ls
import bragwise.shared.generated.resources.flag_lt
import bragwise.shared.generated.resources.flag_lu
import bragwise.shared.generated.resources.flag_lv
import bragwise.shared.generated.resources.flag_ly
import bragwise.shared.generated.resources.flag_ma
import bragwise.shared.generated.resources.flag_md
import bragwise.shared.generated.resources.flag_me
import bragwise.shared.generated.resources.flag_mg
import bragwise.shared.generated.resources.flag_mk
import bragwise.shared.generated.resources.flag_ml
import bragwise.shared.generated.resources.flag_mm
import bragwise.shared.generated.resources.flag_mn
import bragwise.shared.generated.resources.flag_mo
import bragwise.shared.generated.resources.flag_mr
import bragwise.shared.generated.resources.flag_ms
import bragwise.shared.generated.resources.flag_mt
import bragwise.shared.generated.resources.flag_mu
import bragwise.shared.generated.resources.flag_mv
import bragwise.shared.generated.resources.flag_mw
import bragwise.shared.generated.resources.flag_mx
import bragwise.shared.generated.resources.flag_my
import bragwise.shared.generated.resources.flag_mz
import bragwise.shared.generated.resources.flag_na
import bragwise.shared.generated.resources.flag_nc
import bragwise.shared.generated.resources.flag_ne
import bragwise.shared.generated.resources.flag_ng
import bragwise.shared.generated.resources.flag_ni
import bragwise.shared.generated.resources.flag_nl
import bragwise.shared.generated.resources.flag_no
import bragwise.shared.generated.resources.flag_np
import bragwise.shared.generated.resources.flag_nz
import bragwise.shared.generated.resources.flag_om
import bragwise.shared.generated.resources.flag_pa
import bragwise.shared.generated.resources.flag_pe
import bragwise.shared.generated.resources.flag_pf
import bragwise.shared.generated.resources.flag_pg
import bragwise.shared.generated.resources.flag_ph
import bragwise.shared.generated.resources.flag_pk
import bragwise.shared.generated.resources.flag_pl
import bragwise.shared.generated.resources.flag_pr
import bragwise.shared.generated.resources.flag_ps
import bragwise.shared.generated.resources.flag_pt
import bragwise.shared.generated.resources.flag_py
import bragwise.shared.generated.resources.flag_qa
import bragwise.shared.generated.resources.flag_ro
import bragwise.shared.generated.resources.flag_rs
import bragwise.shared.generated.resources.flag_ru
import bragwise.shared.generated.resources.flag_rw
import bragwise.shared.generated.resources.flag_sa
import bragwise.shared.generated.resources.flag_sb
import bragwise.shared.generated.resources.flag_sc
import bragwise.shared.generated.resources.flag_sd
import bragwise.shared.generated.resources.flag_se
import bragwise.shared.generated.resources.flag_sg
import bragwise.shared.generated.resources.flag_si
import bragwise.shared.generated.resources.flag_sk
import bragwise.shared.generated.resources.flag_sl
import bragwise.shared.generated.resources.flag_sm
import bragwise.shared.generated.resources.flag_sn
import bragwise.shared.generated.resources.flag_so
import bragwise.shared.generated.resources.flag_sr
import bragwise.shared.generated.resources.flag_ss
import bragwise.shared.generated.resources.flag_st
import bragwise.shared.generated.resources.flag_sv
import bragwise.shared.generated.resources.flag_sy
import bragwise.shared.generated.resources.flag_sz
import bragwise.shared.generated.resources.flag_tc
import bragwise.shared.generated.resources.flag_td
import bragwise.shared.generated.resources.flag_tg
import bragwise.shared.generated.resources.flag_th
import bragwise.shared.generated.resources.flag_tj
import bragwise.shared.generated.resources.flag_tl
import bragwise.shared.generated.resources.flag_tm
import bragwise.shared.generated.resources.flag_tn
import bragwise.shared.generated.resources.flag_to
import bragwise.shared.generated.resources.flag_tr
import bragwise.shared.generated.resources.flag_tt
import bragwise.shared.generated.resources.flag_tw
import bragwise.shared.generated.resources.flag_tz
import bragwise.shared.generated.resources.flag_ua
import bragwise.shared.generated.resources.flag_ug
import bragwise.shared.generated.resources.flag_us
import bragwise.shared.generated.resources.flag_uy
import bragwise.shared.generated.resources.flag_uz
import bragwise.shared.generated.resources.flag_vc
import bragwise.shared.generated.resources.flag_ve
import bragwise.shared.generated.resources.flag_vg
import bragwise.shared.generated.resources.flag_vi
import bragwise.shared.generated.resources.flag_vn
import bragwise.shared.generated.resources.flag_vu
import bragwise.shared.generated.resources.flag_ws
import bragwise.shared.generated.resources.flag_xk
import bragwise.shared.generated.resources.flag_ye
import bragwise.shared.generated.resources.flag_za
import bragwise.shared.generated.resources.flag_zm
import bragwise.shared.generated.resources.flag_zw
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val FLAG_DRAWABLES: Map<String, DrawableResource> = mapOf(
    "AD" to Res.drawable.flag_ad,
    "AE" to Res.drawable.flag_ae,
    "AF" to Res.drawable.flag_af,
    "AG" to Res.drawable.flag_ag,
    "AI" to Res.drawable.flag_ai,
    "AL" to Res.drawable.flag_al,
    "AM" to Res.drawable.flag_am,
    "AO" to Res.drawable.flag_ao,
    "AR" to Res.drawable.flag_ar,
    "AS" to Res.drawable.flag_as,
    "AT" to Res.drawable.flag_at,
    "AU" to Res.drawable.flag_au,
    "AW" to Res.drawable.flag_aw,
    "AZ" to Res.drawable.flag_az,
    "BA" to Res.drawable.flag_ba,
    "BB" to Res.drawable.flag_bb,
    "BD" to Res.drawable.flag_bd,
    "BE" to Res.drawable.flag_be,
    "BF" to Res.drawable.flag_bf,
    "BG" to Res.drawable.flag_bg,
    "BH" to Res.drawable.flag_bh,
    "BI" to Res.drawable.flag_bi,
    "BJ" to Res.drawable.flag_bj,
    "BM" to Res.drawable.flag_bm,
    "BN" to Res.drawable.flag_bn,
    "BO" to Res.drawable.flag_bo,
    "BR" to Res.drawable.flag_br,
    "BS" to Res.drawable.flag_bs,
    "BT" to Res.drawable.flag_bt,
    "BW" to Res.drawable.flag_bw,
    "BY" to Res.drawable.flag_by,
    "BZ" to Res.drawable.flag_bz,
    "CA" to Res.drawable.flag_ca,
    "CD" to Res.drawable.flag_cd,
    "CF" to Res.drawable.flag_cf,
    "CG" to Res.drawable.flag_cg,
    "CH" to Res.drawable.flag_ch,
    "CI" to Res.drawable.flag_ci,
    "CK" to Res.drawable.flag_ck,
    "CL" to Res.drawable.flag_cl,
    "CM" to Res.drawable.flag_cm,
    "CN" to Res.drawable.flag_cn,
    "CO" to Res.drawable.flag_co,
    "CR" to Res.drawable.flag_cr,
    "CU" to Res.drawable.flag_cu,
    "CV" to Res.drawable.flag_cv,
    "CW" to Res.drawable.flag_cw,
    "CY" to Res.drawable.flag_cy,
    "CZ" to Res.drawable.flag_cz,
    "DE" to Res.drawable.flag_de,
    "DJ" to Res.drawable.flag_dj,
    "DK" to Res.drawable.flag_dk,
    "DM" to Res.drawable.flag_dm,
    "DO" to Res.drawable.flag_do,
    "DZ" to Res.drawable.flag_dz,
    "EC" to Res.drawable.flag_ec,
    "EE" to Res.drawable.flag_ee,
    "EG" to Res.drawable.flag_eg,
    "ER" to Res.drawable.flag_er,
    "ES" to Res.drawable.flag_es,
    "ET" to Res.drawable.flag_et,
    "FI" to Res.drawable.flag_fi,
    "FJ" to Res.drawable.flag_fj,
    "FO" to Res.drawable.flag_fo,
    "FR" to Res.drawable.flag_fr,
    "GA" to Res.drawable.flag_ga,
    "GB" to Res.drawable.flag_gb,
    "GB-ENG" to Res.drawable.flag_gb_eng,
    "GB-NIR" to Res.drawable.flag_gb_nir,
    "GB-SCT" to Res.drawable.flag_gb_sct,
    "GB-WLS" to Res.drawable.flag_gb_wls,
    "GD" to Res.drawable.flag_gd,
    "GE" to Res.drawable.flag_ge,
    "GH" to Res.drawable.flag_gh,
    "GI" to Res.drawable.flag_gi,
    "GM" to Res.drawable.flag_gm,
    "GN" to Res.drawable.flag_gn,
    "GQ" to Res.drawable.flag_gq,
    "GR" to Res.drawable.flag_gr,
    "GT" to Res.drawable.flag_gt,
    "GU" to Res.drawable.flag_gu,
    "GW" to Res.drawable.flag_gw,
    "GY" to Res.drawable.flag_gy,
    "HK" to Res.drawable.flag_hk,
    "HN" to Res.drawable.flag_hn,
    "HR" to Res.drawable.flag_hr,
    "HT" to Res.drawable.flag_ht,
    "HU" to Res.drawable.flag_hu,
    "ID" to Res.drawable.flag_id,
    "IE" to Res.drawable.flag_ie,
    "IL" to Res.drawable.flag_il,
    "IN" to Res.drawable.flag_in,
    "IQ" to Res.drawable.flag_iq,
    "IR" to Res.drawable.flag_ir,
    "IS" to Res.drawable.flag_is,
    "IT" to Res.drawable.flag_it,
    "JE" to Res.drawable.flag_je,
    "JM" to Res.drawable.flag_jm,
    "JO" to Res.drawable.flag_jo,
    "JP" to Res.drawable.flag_jp,
    "KE" to Res.drawable.flag_ke,
    "KG" to Res.drawable.flag_kg,
    "KH" to Res.drawable.flag_kh,
    "KM" to Res.drawable.flag_km,
    "KN" to Res.drawable.flag_kn,
    "KP" to Res.drawable.flag_kp,
    "KR" to Res.drawable.flag_kr,
    "KW" to Res.drawable.flag_kw,
    "KY" to Res.drawable.flag_ky,
    "KZ" to Res.drawable.flag_kz,
    "LA" to Res.drawable.flag_la,
    "LB" to Res.drawable.flag_lb,
    "LC" to Res.drawable.flag_lc,
    "LI" to Res.drawable.flag_li,
    "LK" to Res.drawable.flag_lk,
    "LR" to Res.drawable.flag_lr,
    "LS" to Res.drawable.flag_ls,
    "LT" to Res.drawable.flag_lt,
    "LU" to Res.drawable.flag_lu,
    "LV" to Res.drawable.flag_lv,
    "LY" to Res.drawable.flag_ly,
    "MA" to Res.drawable.flag_ma,
    "MD" to Res.drawable.flag_md,
    "ME" to Res.drawable.flag_me,
    "MG" to Res.drawable.flag_mg,
    "MK" to Res.drawable.flag_mk,
    "ML" to Res.drawable.flag_ml,
    "MM" to Res.drawable.flag_mm,
    "MN" to Res.drawable.flag_mn,
    "MO" to Res.drawable.flag_mo,
    "MR" to Res.drawable.flag_mr,
    "MS" to Res.drawable.flag_ms,
    "MT" to Res.drawable.flag_mt,
    "MU" to Res.drawable.flag_mu,
    "MV" to Res.drawable.flag_mv,
    "MW" to Res.drawable.flag_mw,
    "MX" to Res.drawable.flag_mx,
    "MY" to Res.drawable.flag_my,
    "MZ" to Res.drawable.flag_mz,
    "NA" to Res.drawable.flag_na,
    "NC" to Res.drawable.flag_nc,
    "NE" to Res.drawable.flag_ne,
    "NG" to Res.drawable.flag_ng,
    "NI" to Res.drawable.flag_ni,
    "NL" to Res.drawable.flag_nl,
    "NO" to Res.drawable.flag_no,
    "NP" to Res.drawable.flag_np,
    "NZ" to Res.drawable.flag_nz,
    "OM" to Res.drawable.flag_om,
    "PA" to Res.drawable.flag_pa,
    "PE" to Res.drawable.flag_pe,
    "PF" to Res.drawable.flag_pf,
    "PG" to Res.drawable.flag_pg,
    "PH" to Res.drawable.flag_ph,
    "PK" to Res.drawable.flag_pk,
    "PL" to Res.drawable.flag_pl,
    "PR" to Res.drawable.flag_pr,
    "PS" to Res.drawable.flag_ps,
    "PT" to Res.drawable.flag_pt,
    "PY" to Res.drawable.flag_py,
    "QA" to Res.drawable.flag_qa,
    "RO" to Res.drawable.flag_ro,
    "RS" to Res.drawable.flag_rs,
    "RU" to Res.drawable.flag_ru,
    "RW" to Res.drawable.flag_rw,
    "SA" to Res.drawable.flag_sa,
    "SB" to Res.drawable.flag_sb,
    "SC" to Res.drawable.flag_sc,
    "SD" to Res.drawable.flag_sd,
    "SE" to Res.drawable.flag_se,
    "SG" to Res.drawable.flag_sg,
    "SI" to Res.drawable.flag_si,
    "SK" to Res.drawable.flag_sk,
    "SL" to Res.drawable.flag_sl,
    "SM" to Res.drawable.flag_sm,
    "SN" to Res.drawable.flag_sn,
    "SO" to Res.drawable.flag_so,
    "SR" to Res.drawable.flag_sr,
    "SS" to Res.drawable.flag_ss,
    "ST" to Res.drawable.flag_st,
    "SV" to Res.drawable.flag_sv,
    "SY" to Res.drawable.flag_sy,
    "SZ" to Res.drawable.flag_sz,
    "TC" to Res.drawable.flag_tc,
    "TD" to Res.drawable.flag_td,
    "TG" to Res.drawable.flag_tg,
    "TH" to Res.drawable.flag_th,
    "TJ" to Res.drawable.flag_tj,
    "TL" to Res.drawable.flag_tl,
    "TM" to Res.drawable.flag_tm,
    "TN" to Res.drawable.flag_tn,
    "TO" to Res.drawable.flag_to,
    "TR" to Res.drawable.flag_tr,
    "TT" to Res.drawable.flag_tt,
    "TW" to Res.drawable.flag_tw,
    "TZ" to Res.drawable.flag_tz,
    "UA" to Res.drawable.flag_ua,
    "UG" to Res.drawable.flag_ug,
    "US" to Res.drawable.flag_us,
    "UY" to Res.drawable.flag_uy,
    "UZ" to Res.drawable.flag_uz,
    "VC" to Res.drawable.flag_vc,
    "VE" to Res.drawable.flag_ve,
    "VG" to Res.drawable.flag_vg,
    "VI" to Res.drawable.flag_vi,
    "VN" to Res.drawable.flag_vn,
    "VU" to Res.drawable.flag_vu,
    "WS" to Res.drawable.flag_ws,
    "XK" to Res.drawable.flag_xk,
    "YE" to Res.drawable.flag_ye,
    "ZA" to Res.drawable.flag_za,
    "ZM" to Res.drawable.flag_zm,
    "ZW" to Res.drawable.flag_zw,
)

fun flagDrawable(code: String): DrawableResource? = FLAG_DRAWABLES[code.uppercase()]

@Composable
fun FlagImage(code: String, size: Dp, modifier: Modifier = Modifier) {
    val res = flagDrawable(code) ?: return
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}
