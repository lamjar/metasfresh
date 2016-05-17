DROP FUNCTION IF EXISTS report.umsatzliste_bpartner_week_report (IN numeric, IN numeric, IN integer, IN integer, IN numeric, IN numeric, IN integer, IN integer, IN character varying, IN numeric, IN numeric, IN numeric, IN numeric );
CREATE OR REPLACE FUNCTION report.umsatzliste_bpartner_week_report
	(
		IN Base_Year_Start numeric, --$1
		IN Base_Year_End numeric, --$2
		IN Base_Week_Start integer, --$3
		IN Base_Week_End integer,  --$4
		IN Comp_Year_Start numeric, --$5
		IN Comp_Year_End numeric, --$6
		IN Comp_Week_Start integer,  --$7
		IN Comp_Week_End integer,  --$8
		IN issotrx character varying, --$9
		IN C_BPartner_ID numeric,  --$10
		IN M_Product_ID numeric, --$11
		IN M_Product_Category_ID numeric, --$12
		IN M_AttributeSetInstance_ID numeric --$13
	) 
	RETURNS TABLE
	(
		bp_name character varying(60),
		pc_name character varying(60),
		p_name character varying(255),
		sameperiodsum numeric,
		compperiodsum numeric,
		perioddifference numeric,
		perioddiffpercentage numeric,
		Base_Week_Start character varying(10),
		Base_Week_End character varying(10),
		Comp_Week_Start character varying(10),
		Comp_Week_End character varying(10),
		param_IsSOTrx  character varying,
		param_bp character varying(60),
		param_product character varying(255),
		param_Product_Category character varying(60),
		Param_Attributes character varying(255)
	)
AS
$$
SELECT
	bp.Name AS bp_name,
	pc.Name AS pc_name, 
	p.Name AS P_name,
	SamePeriodSum,
	CompPeriodSum,
	SamePeriodSum - CompPeriodSum AS PeriodDifference,
	CASE WHEN SamePeriodSum - CompPeriodSum != 0 AND CompPeriodSum != 0
		THEN (SamePeriodSum - CompPeriodSum) / CompPeriodSum * 100 ELSE NULL
	END AS PeriodDiffPercentage,
	
	$3 || ' ' || (select fiscalyear from c_year where c_year_id =$1) AS Base_Week_Start,
	$4 || ' ' || (select fiscalyear from c_year where c_year_id =$2) AS Base_Week_End,
	COALESCE( ($7 || ' ' || (select fiscalyear from c_year where c_year_id =$5)),'') AS Comp_Week_Start,
	COALESCE( ($8 || ' ' || (select fiscalyear from c_year where c_year_id =$6)),'') AS Comp_Week_End,
	 $9 AS param_IsSOTrx,
	(SELECT name FROM C_BPartner WHERE C_BPartner_ID = $10) AS param_bp,
	(SELECT name FROM M_Product WHERE M_Product_ID = $11) AS param_product,
	(SELECT name FROM M_Product_Category WHERE M_Product_Category_ID = $12) AS param_Product_Category,
	(SELECT String_Agg(ai_value, ', ' ORDER BY ai_Value) FROM Report.fresh_Attributes WHERE M_AttributeSetInstance_ID = $13) AS Param_Attributes
FROM
	(
		SELECT
			io.C_BPartner_ID,
			iol.M_Product_ID,
			SUM( CASE WHEN( io.MovementDate::date >= (select (to_timestamp($3 || ' ' ||(select fiscalyear from c_year where c_year_id =$1),'IW IYYY')))::date   -- base_week_start
						AND io.MovementDate::date <= (select (to_timestamp($4 || ' ' ||(select fiscalyear from c_year where c_year_id =$2),'IW IYYY')+ interval '6' day))::date ) -- base_week_end
				THEN COALESCE(ic.PriceEntered_Override, ic.PriceEntered) * iol.MovementQty 
				ELSE 0 
			END) AS SamePeriodSum,
			SUM( CASE WHEN( io.MovementDate::date >= (select (to_timestamp($7 || ' ' ||(select fiscalyear from c_year where c_year_id =$5),'IW IYYY')))::date  -- comp_week_start 
						AND io.MovementDate::date <= (select (to_timestamp($8 || ' ' ||(select fiscalyear from c_year where c_year_id =$6),'IW IYYY')+ interval '6' day))::date ) -- comp_week_end 
			  THEN COALESCE(ic.PriceEntered_Override, ic.PriceEntered) * iol.MovementQty  
			  ELSE 0 
			END) AS CompPeriodSum	
			
			FROM  C_InvoiceCandidate_InOutLine iciol	 
			INNER JOIN C_Invoice_Candidate ic ON iciol.C_Invoice_Candidate_ID = ic.C_Invoice_Candidate_ID
			INNER JOIN M_InOutLine iol ON iol.M_InOutLine_ID = iciol.M_InOutLine_ID
			INNER JOIN M_InOut io ON iol.M_InOut_ID = io.M_InOut_ID

			INNER JOIN M_Product p ON iol.M_Product_ID = p.M_Product_ID
		WHERE
			( 
			((io.MovementDate::date >= (select (to_timestamp($3 || ' ' ||(select fiscalyear from c_year where c_year_id =$1),'IW IYYY')))::date ) -- base_week_start 
				AND io.MovementDate::date<= (select (to_timestamp($4 || ' ' ||(select fiscalyear from c_year where c_year_id =$2),'IW IYYY')+ interval '6' day))::date)  -- base_week_end
			OR ((io.MovementDate::date >= (select (to_timestamp($7 || ' ' ||(select fiscalyear from c_year where c_year_id =$5),'IW IYYY')))::date )  -- comp_week_start 
				AND io.MovementDate::date <= (select (to_timestamp($8 || ' ' ||(select fiscalyear from c_year where c_year_id =$6),'IW IYYY')+ interval '6' day))::date) -- comp_week_end 
			)  
			AND io.IsSOtrx = $9
			AND ( CASE WHEN $10 IS NULL THEN TRUE ELSE io.C_BPartner_ID = $10 END )
			AND ( CASE WHEN $11 IS NULL THEN TRUE ELSE p.M_Product_ID = $11 END AND p.M_Product_ID IS NOT NULL )
			AND ( CASE WHEN $12 IS NULL THEN TRUE ELSE p.M_Product_Category_ID = $12 END 
				-- It was a requirement to not have HU Packing material within the sums of this report 
				AND p.M_Product_Category_ID != (SELECT value::numeric FROM AD_SysConfig WHERE name = 'PackingMaterialProductCategoryID')
			)
			AND ( 
				CASE WHEN EXISTS ( SELECT ai_value FROM report.fresh_Attributes WHERE M_AttributeSetInstance_ID = $13 )
				THEN ( 
					EXISTS (
						SELECT	0
						FROM	report.fresh_Attributes a
							INNER JOIN report.fresh_Attributes pa ON a.at_value = pa.at_value AND a.ai_value = pa.ai_value 
								AND pa.M_AttributeSetInstance_ID = $13			
						WHERE	a.M_AttributeSetInstance_ID = iol.M_AttributeSetInstance_ID
					)
					AND NOT EXISTS (
						SELECT	0
						FROM	report.fresh_Attributes pa
							LEFT OUTER JOIN report.fresh_Attributes a ON a.at_value = pa.at_value AND a.ai_value = pa.ai_value 
								AND a.M_AttributeSetInstance_ID = iol.M_AttributeSetInstance_ID
						WHERE	pa.M_AttributeSetInstance_ID = $13
							AND a.M_AttributeSetInstance_ID IS null
					)
				)
				ELSE TRUE END
			)
			AND io.docstatus in ('CO','CL')
		GROUP BY
			io.C_BPartner_ID,
			iol.M_Product_ID
	) a
	INNER JOIN C_BPartner bp ON a.C_BPartner_ID = bp.C_BPartner_ID
	INNER JOIN M_Product p ON a.M_Product_ID = p.M_Product_ID
	INNER JOIN M_Product_Category pc ON p.M_Product_Category_ID = pc.M_Product_Category_ID
	
	ORDER BY
	bp_name, pc_name NULLS LAST, p_name
$$
LANGUAGE sql STABLE;